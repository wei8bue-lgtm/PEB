package com.peb.service.cim;

import com.peb.exception.ObjectNotFoundException;
import com.peb.model.cim.*;
import com.peb.model.ebom.EBOMObject;
import com.peb.model.product.*;
import com.peb.model.structure.StructureRelation;
import com.peb.model.structure.StructureNode;
import com.peb.model.variant.ProductConfiguration;
import com.peb.model.variant.VariantOption;
import com.peb.repository.StructureRelationRepository;
import com.peb.repository.cim.*;
import com.peb.repository.product.*;
import com.peb.service.ConfigurationService;
import com.peb.service.EBOMService;
import com.peb.service.IDGeneratorService;
import com.peb.service.StructureService;
import com.peb.service.product.ProductBOMStructureSnapshotService;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CimService {

    private final CimObjectRepository cimObjectRepository;
    private final CimRevisionRepository cimRevisionRepository;
    private final CimAttributeSetRepository cimAttributeSetRepository;
    private final CimStructureRelationRepository cimStructureRelationRepository;
    private final CimValidationResultRepository cimValidationResultRepository;
    private final CimManufacturingLinkRepository cimManufacturingLinkRepository;
    private final StructureRelationRepository structureRelationRepository;

    private final EBOMService ebomService;
    private final ConfigurationService configurationService;
    private final IDGeneratorService idGeneratorService;
    private final StructureService structureService;
    private final ProductBOMStructureSnapshotService productBOMStructureSnapshotService;

    private final ProductDataRepository productDataRepository;
    private final ProductDataRevisionRepository productDataRevisionRepository;
    private final ProductBOMRepository productBOMRepository;
    private final ProductBOMRevisionRepository productBOMRevisionRepository;
    private final ManufacturingBOMRepository manufacturingBOMRepository;
    private final ManufacturingBOMRevisionRepository manufacturingBOMRevisionRepository;

    @Transactional
    public CimObject createFromMae(String maeObjectId) {
        return cimObjectRepository.findBySourceMaeObjectId(maeObjectId)
                .map(existing -> {
                    syncFromMae(existing.getId());
                    return existing;
                })
                .orElseGet(() -> createNewFromMae(maeObjectId));
    }

    @Transactional
    public CimRevision syncFromMae(String cimId) {
        return syncFromMae(cimId, new HashSet<>());
    }

    @Transactional(readOnly = true)
    public CimStructureNode getStructureTree(Long cimRevisionId) {
        return buildStructureNode(findRevision(cimRevisionId), null, 0, new HashSet<>());
    }

    @Transactional(readOnly = true)
    public List<CimStructureRelation> getParentRelations(Long cimRevisionId) {
        return cimStructureRelationRepository.findByChildCimRevisionRevisionId(cimRevisionId);
    }

    private CimRevision syncFromMae(String cimId, Set<String> syncPath) {
        CimObject cimObject = cimObjectRepository.findById(cimId)
                .orElseThrow(() -> new ObjectNotFoundException("CIM object not found: " + cimId));

        EBOMObject maeObject = ebomService.getEBOMObject(cimObject.getSourceMaeObjectId());

        String syncKey = buildSyncKey(maeObject);
        if (!syncPath.add(syncKey)) {
            log.warn("Detected cyclic MAE structure while syncing CIM object {} via {}", cimId, syncKey);
            return cimRevisionRepository.findFirstByCimObjectIdOrderByRevisionDesc(cimId)
                    .orElseThrow(() -> new ObjectNotFoundException("CIM revision not found for object: " + cimId));
        }

        try {
            cimObject.setName(maeObject.getName());
            cimObject.setDepartment(maeObject.getDepartment());
            cimObjectRepository.save(cimObject);

            CimRevision latest = cimRevisionRepository
                    .findFirstByCimObjectIdOrderByRevisionDesc(cimId)
                    .orElse(null);

            CimRevision revision;
            if (latest == null) {
                revision = new CimRevision();
                revision.setRevision("01");
            } else if (latest.getReleaseStatus() == ReleaseStatus.WORKING || latest.getReleaseStatus() == ReleaseStatus.IN_REVIEW) {
                revision = latest;
            } else {
                revision = new CimRevision();
                revision.setRevision(incrementRevision(latest.getRevision()));
            }

            revision.setCimObject(cimObject);
            revision.setName(cimObject.getName());
            revision.setDepartment(cimObject.getDepartment());
            revision.setSourceMaeObjectId(maeObject.getId());
            revision.setSourceMaeObjectType(maeObject.getObjectType());
            revision.setSourceMaeRevisionId(maeObject.getLatestRevision() != null
                    ? maeObject.getLatestRevision().getRevisionId()
                    : null);
            revision.setReleaseStatus(ReleaseStatus.WORKING);
            revision.setContentHash(buildContentHash(maeObject));

            CimRevision savedRevision = cimRevisionRepository.save(revision);
            upsertAttributeSet(savedRevision, maeObject, "MAE_SYNC");
            syncStructureRelationsFromMae(savedRevision, maeObject, syncPath);
            runValidation(savedRevision);

            return savedRevision;
        } finally {
            syncPath.remove(syncKey);
        }
    }

    @Transactional
    public CimRevision submitForReview(Long cimRevisionId) {
        CimRevision revision = findRevision(cimRevisionId);
        if (revision.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new IllegalStateException("Only WORKING revisions can be submitted for review");
        }
        revision.setReleaseStatus(ReleaseStatus.IN_REVIEW);
        return cimRevisionRepository.save(revision);
    }

    @Transactional
    public CimRevision releaseRevision(Long cimRevisionId, String releaseComment) {
        CimRevision revision = findRevision(cimRevisionId);

        if (revision.getReleaseStatus() != ReleaseStatus.WORKING
                && revision.getReleaseStatus() != ReleaseStatus.IN_REVIEW) {
            throw new IllegalStateException("Only WORKING or IN_REVIEW revisions can be released");
        }

        runValidation(revision);
        List<CimValidationResult> blockers = cimValidationResultRepository
                .findByCimRevisionRevisionIdAndBlockingTrue(cimRevisionId);

        if (!blockers.isEmpty()) {
            throw new IllegalStateException("Release blocked by validation errors: " + blockers.size());
        }

        revision.setReleaseStatus(ReleaseStatus.RELEASED);
        revision.setReleaseComment(releaseComment);
        revision.setEffectiveFrom(LocalDateTime.now());
        CimRevision released = cimRevisionRepository.save(revision);

        List<CimRevision> revisions = cimRevisionRepository
                .findByCimObjectIdOrderByRevisionDesc(released.getCimObject().getId());
        for (CimRevision candidate : revisions) {
            if (!candidate.getRevisionId().equals(released.getRevisionId())
                    && candidate.getReleaseStatus() == ReleaseStatus.RELEASED) {
                candidate.setReleaseStatus(ReleaseStatus.OBSOLETE);
                cimRevisionRepository.save(candidate);
            }
        }

        CimObject cimObject = released.getCimObject();
        cimObject.setLifecycleState(CimLifecycleState.ACTIVE);
        cimObjectRepository.save(cimObject);

        return released;
    }

    @Transactional
    public List<CimManufacturingLink> importToManufacturing(Long cimRevisionId,
                                                            CimImportMode importMode,
                                                            Long configurationId,
                                                            String productDataName,
                                                            String productBomName,
                                                            String manufacturingBomName) {
        CimRevision revision = findRevision(cimRevisionId);
        if (revision.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new IllegalStateException("Only RELEASED CIM revisions can be imported to manufacturing");
        }

        ProductConfiguration configuration = resolveImportConfiguration(revision, configurationId);
    StructureNode importedStructure = buildImportStructure(revision, configuration);
    int importedNodeCount = countNodes(importedStructure);

        String user = currentUser();
        String configSuffix = configuration != null ? " / " + configuration.getName() : "";

        ProductData productData = new ProductData();
        productData.setId(idGeneratorService.generateId(ObjectType.PRODUCT_DATA));
        productData.setName(defaultIfBlank(productDataName, "CIM " + revision.getCimObject().getId() + configSuffix));
        productData.setDepartment(revision.getDepartment());
        productData.setProductType("CIM");
        productData.setDescription(buildImportDescription(revision, configuration, importedNodeCount));
        productData = productDataRepository.save(productData);

        ProductDataRevision pdRevision = new ProductDataRevision();
        pdRevision.setProductData(productData);
        pdRevision.setRevision("01");
        pdRevision.setStatus(ManufacturingStatus.WORKING);
        pdRevision.setCreatedBy(user);
        productDataRevisionRepository.save(pdRevision);

        ProductBOM productBOM = new ProductBOM();
        productBOM.setName(defaultIfBlank(productBomName, "PBOM " + productData.getId() + configSuffix));
        productBOM.setProductData(productData);
        productBOM.setDescription(buildImportDescription(revision, configuration, importedNodeCount));
        productBOM.setSourceCimId(revision.getCimObject().getId());
        productBOM.setSourceCimRevision(revision.getRevision());
        productBOM.setSourceConfigurationId(configuration != null ? configuration.getId() : null);
        productBOM.setSourceConfigurationName(configuration != null ? configuration.getName() : null);
        productBOM.setCimImportedAt(LocalDateTime.now());
        productBOM.setCimImportedBy(user);
        productBOM = productBOMRepository.save(productBOM);
        productBOMStructureSnapshotService.replaceSnapshot(productBOM, importedStructure);

        ProductBOMRevision pbRevision = new ProductBOMRevision();
        pbRevision.setProductBOM(productBOM);
        pbRevision.setRevision("01");
        pbRevision.setStatus(ManufacturingStatus.WORKING);
        pbRevision.setCreatedBy(user);
        productBOMRevisionRepository.save(pbRevision);

        ManufacturingBOM manufacturingBOM = new ManufacturingBOM();
        manufacturingBOM.setName(defaultIfBlank(manufacturingBomName, "MBOM " + productBOM.getId() + configSuffix));
        manufacturingBOM.setDescription(buildImportDescription(revision, configuration, importedNodeCount));
        manufacturingBOM.setSourceCimId(revision.getCimObject().getId());
        manufacturingBOM.setSourceCimRevision(revision.getRevision());
        manufacturingBOM.setSourceConfigurationId(configuration != null ? configuration.getId() : null);
        manufacturingBOM.setSourceConfigurationName(configuration != null ? configuration.getName() : null);
        manufacturingBOM.setCimImportedAt(LocalDateTime.now());
        manufacturingBOM.setCimImportedBy(user);
        manufacturingBOM.getProductBOMs().add(productBOM);
        manufacturingBOM = manufacturingBOMRepository.save(manufacturingBOM);

        ManufacturingBOMRevision mbRevision = new ManufacturingBOMRevision();
        mbRevision.setManufacturingBOM(manufacturingBOM);
        mbRevision.setRevision("01");
        mbRevision.setStatus(ManufacturingStatus.WORKING);
        mbRevision.setCreatedBy(user);
        manufacturingBOMRevisionRepository.save(mbRevision);

        List<CimManufacturingLink> links = new ArrayList<>();
        links.add(saveLink(revision, CimManufacturingTargetType.PRODUCT_DATA, productData.getId(), importMode, user, configuration, importedNodeCount));
        links.add(saveLink(revision, CimManufacturingTargetType.PRODUCT_BOM, String.valueOf(productBOM.getId()), importMode, user, configuration, importedNodeCount));
        links.add(saveLink(revision, CimManufacturingTargetType.MANUFACTURING_BOM, String.valueOf(manufacturingBOM.getId()), importMode, user, configuration, importedNodeCount));

        log.info("Imported CIM revision {} into manufacturing objects PD={}, PBOM={}, MBOM={} with configuration {}",
            cimRevisionId, productData.getId(), productBOM.getId(), manufacturingBOM.getId(),
            configuration != null ? configuration.getId() : null);

        return links;
    }

    @Transactional(readOnly = true)
    public List<CimObject> getAllCimObjects() {
        return cimObjectRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<CimObject> getTopLevelMachineCimObjects() {
        return cimObjectRepository.findBySourceMaeObjectTypeOrderByIdAsc(ObjectType.MACHINE);
    }

    @Transactional(readOnly = true)
    public List<CimRevision> getRevisions(String cimId) {
        return cimRevisionRepository.findByCimObjectIdOrderByRevisionDesc(cimId);
    }

    @Transactional(readOnly = true)
    public CimObject getCimObject(String cimId) {
        return cimObjectRepository.findById(cimId)
                .orElseThrow(() -> new ObjectNotFoundException("CIM object not found: " + cimId));
    }

    @Transactional(readOnly = true)
    public CimRevision getRevision(Long cimRevisionId) {
        return findRevision(cimRevisionId);
    }

    @Transactional(readOnly = true)
    public List<CimValidationResult> getValidationResults(Long cimRevisionId) {
        return cimValidationResultRepository.findByCimRevisionRevisionId(cimRevisionId);
    }

    @Transactional(readOnly = true)
    public List<CimManufacturingLink> getManufacturingLinks(Long cimRevisionId) {
        return cimManufacturingLinkRepository.findBySourceCimRevisionRevisionId(cimRevisionId);
    }

    @Transactional(readOnly = true)
    public List<CimStructureRelation> getStructureRelations(Long cimRevisionId) {
        return cimStructureRelationRepository.findByParentCimRevisionRevisionId(cimRevisionId);
    }

    @Transactional
    public void runValidation(Long cimRevisionId) {
        CimRevision revision = findRevision(cimRevisionId);
        runValidation(revision);
    }

    private CimObject createNewFromMae(String maeObjectId) {
        return createNewFromMae(maeObjectId, new HashSet<>());
    }

    private CimObject createNewFromMae(String maeObjectId, Set<String> syncPath) {
        EBOMObject maeObject = ebomService.getEBOMObject(maeObjectId);

        CimObject cimObject = new CimObject();
        cimObject.setId(idGeneratorService.generateId(ObjectType.CIM));
        cimObject.setName(maeObject.getName());
        cimObject.setDepartment(maeObject.getDepartment());
        cimObject.setLifecycleState(CimLifecycleState.DRAFT);
        cimObject.setSourceMaeObjectId(maeObject.getId());
        cimObject.setSourceMaeObjectType(maeObject.getObjectType());
        cimObject.setSourceMaeRevisionId(maeObject.getLatestRevision() != null
                ? maeObject.getLatestRevision().getRevisionId()
                : null);

        CimObject savedObject = cimObjectRepository.save(cimObject);

        CimRevision revision = new CimRevision();
        revision.setCimObject(savedObject);
        revision.setName(savedObject.getName());
        revision.setDepartment(savedObject.getDepartment());
        revision.setRevision("01");
        revision.setReleaseStatus(ReleaseStatus.WORKING);
        revision.setSourceMaeObjectId(maeObject.getId());
        revision.setSourceMaeObjectType(maeObject.getObjectType());
        revision.setSourceMaeRevisionId(savedObject.getSourceMaeRevisionId());
        revision.setContentHash(buildContentHash(maeObject));

        CimRevision savedRevision = cimRevisionRepository.save(revision);
        upsertAttributeSet(savedRevision, maeObject, "MAE_SYNC");
    syncStructureRelationsFromMae(savedRevision, maeObject, syncPath);
        runValidation(savedRevision);

        return savedObject;
    }

    private CimRevision findRevision(Long cimRevisionId) {
        return cimRevisionRepository.findById(cimRevisionId)
                .orElseThrow(() -> new ObjectNotFoundException("CIM revision not found: " + cimRevisionId));
    }

    private void upsertAttributeSet(CimRevision revision,
                                    EBOMObject maeObject,
                                    String source) {
        CimAttributeSet attributeSet = cimAttributeSetRepository
                .findByCimRevisionRevisionId(revision.getRevisionId())
                .orElseGet(CimAttributeSet::new);

        attributeSet.setCimRevision(revision);
        attributeSet.setMaterial(maeObject.getMaterial());
        attributeSet.setMass(maeObject.getMass());
        attributeSet.setSupplier(maeObject.getSupplier());
        attributeSet.setAttributeSource(source);

        cimAttributeSetRepository.save(attributeSet);
    }

    private void syncStructureRelationsFromMae(CimRevision parentRevision,
                                               EBOMObject maeObject,
                                               Set<String> syncPath) {
        if (maeObject == null || maeObject.getId() == null) {
            return;
        }

        Long sourceMaeRevisionId = maeObject.getLatestRevision() != null
                ? maeObject.getLatestRevision().getRevisionId()
                : parentRevision.getSourceMaeRevisionId();

        List<StructureRelation> maeChildren = structureRelationRepository
                .findByParentIdAndParentRevisionId(maeObject.getId(), sourceMaeRevisionId);

        List<CimStructureRelation> existingRelations = cimStructureRelationRepository
                .findByParentCimRevisionRevisionId(parentRevision.getRevisionId());
        Map<String, CimStructureRelation> existingByMaeChildId = new HashMap<>();
        for (CimStructureRelation existingRelation : existingRelations) {
            CimRevision childRevision = existingRelation.getChildCimRevision();
            if (childRevision != null && childRevision.getSourceMaeObjectId() != null) {
                existingByMaeChildId.put(childRevision.getSourceMaeObjectId(), existingRelation);
            }
        }

        Set<Long> desiredRelationIds = new HashSet<>();

        for (StructureRelation maeRelation : maeChildren) {
            String childMaeObjectId = maeRelation.getChildId();
            if (childMaeObjectId == null || childMaeObjectId.isBlank()) {
                continue;
            }
            if (!isMaeCimEligibleObjectId(childMaeObjectId)) {
                log.debug("Skipping non-eBOM child relation for CIM sync: {}", childMaeObjectId);
                continue;
            }

            CimObject childCimObject = cimObjectRepository.findBySourceMaeObjectId(childMaeObjectId)
                    .orElseGet(() -> createNewFromMae(childMaeObjectId, syncPath));

            CimRevision childCimRevision = syncFromMae(childCimObject.getId(), syncPath);

            CimStructureRelation relation = existingByMaeChildId.get(childMaeObjectId);
            if (relation == null) {
                relation = new CimStructureRelation();
                relation.setParentCimRevision(parentRevision);
            }

            relation.setChildCimRevision(childCimRevision);
            relation.setQuantity(maeRelation.getQuantity() != null ? maeRelation.getQuantity() : 1);
            relation.setPositionNumber(maeRelation.getPositionNumber());
            relation.setReferenceDesignator(maeRelation.getReferenceDesignator());
            relation.setBmk(maeRelation.getBmk());
            relation.setUsageContext(maeRelation.getRelationType() != null ? maeRelation.getRelationType().name() : null);
            relation.setRelationType(maeRelation.getRelationType() != null ? maeRelation.getRelationType().name() : null);
            relation.setConfigRule(maeRelation.getConfigRule());
            relation.setAlternativeGroup(maeRelation.getAlternativeGroup());
            relation.setAlternativePriority(maeRelation.getAlternativePriority());

            CimStructureRelation savedRelation = cimStructureRelationRepository.save(relation);
            desiredRelationIds.add(savedRelation.getRelationId());
        }

        for (CimStructureRelation existingRelation : existingRelations) {
            if (!desiredRelationIds.contains(existingRelation.getRelationId())) {
                cimStructureRelationRepository.delete(existingRelation);
            }
        }
    }

    private CimStructureNode buildStructureNode(CimRevision revision,
                                                CimStructureRelation viaRelation,
                                                int depth,
                                                Set<Long> visitedRevisionIds) {
        if (depth > 20) {
            throw new IllegalStateException("CIM structure depth exceeds maximum (20 levels)");
        }

        CimStructureNode node = new CimStructureNode();
        node.setCimRevisionId(revision.getRevisionId());
        node.setCimId(revision.getCimObject().getId());
        node.setName(revision.getName());
        node.setRevision(revision.getRevision());
        node.setReleaseStatus(revision.getReleaseStatus() != null ? revision.getReleaseStatus().name() : null);
        node.setSourceMaeObjectId(revision.getSourceMaeObjectId());
        node.setSourceMaeObjectType(revision.getSourceMaeObjectType() != null ? revision.getSourceMaeObjectType().name() : null);

        if (viaRelation != null) {
            node.setQuantity(viaRelation.getQuantity());
            node.setPositionNumber(viaRelation.getPositionNumber());
            node.setReferenceDesignator(viaRelation.getReferenceDesignator());
            node.setBmk(viaRelation.getBmk());
            node.setUsageContext(viaRelation.getUsageContext());
            node.setRelationType(viaRelation.getRelationType());
            node.setConfigRule(viaRelation.getConfigRule());
            node.setAlternativeGroup(viaRelation.getAlternativeGroup());
            node.setAlternativePriority(viaRelation.getAlternativePriority());
        }

        if (!visitedRevisionIds.add(revision.getRevisionId())) {
            return node;
        }

        try {
            List<CimStructureRelation> childRelations = cimStructureRelationRepository
                    .findByParentCimRevisionRevisionId(revision.getRevisionId());
            for (CimStructureRelation childRelation : childRelations) {
                if (childRelation.getChildCimRevision() == null) {
                    continue;
                }
                node.getChildren().add(buildStructureNode(
                        childRelation.getChildCimRevision(),
                        childRelation,
                        depth + 1,
                        visitedRevisionIds));
            }
            return node;
        } finally {
            visitedRevisionIds.remove(revision.getRevisionId());
        }
    }

    private String buildSyncKey(EBOMObject maeObject) {
        Long revisionId = maeObject.getLatestRevision() != null
                ? maeObject.getLatestRevision().getRevisionId()
                : null;
        return maeObject.getId() + ":" + revisionId;
    }

    private ProductConfiguration resolveImportConfiguration(CimRevision revision, Long configurationId) {
        List<ProductConfiguration> availableConfigurations = configurationService
                .getConfigurationsForObject(revision.getSourceMaeObjectId());

        if (availableConfigurations.isEmpty()) {
            return null;
        }

        if (configurationId == null) {
            throw new IllegalStateException("A source MAE configuration must be selected before import to manufacturing");
        }

        ProductConfiguration configuration = configurationService.getConfiguration(configurationId);
        if (!revision.getSourceMaeObjectId().equals(configuration.getRootObjectId())) {
            throw new IllegalStateException("Selected configuration does not belong to the source MAE object");
        }

        List<String> validationErrors = configurationService.validateConfiguration(configurationId);
        if (!validationErrors.isEmpty()) {
            throw new IllegalStateException("Selected configuration is incomplete: " + String.join(" | ", validationErrors));
        }

        return configuration;
    }

    private String buildImportDescription(CimRevision revision, ProductConfiguration configuration, int importedNodeCount) {
        String description = "Generated from CIM revision " + revision.getRevision()
                + " (" + revision.getCimObject().getId() + ")";
        description += " with resolved structure size " + importedNodeCount;
        if (configuration == null) {
            return description;
        }
        return description + " using configuration " + configuration.getName() + " (#" + configuration.getId() + ")";
    }

    private StructureNode buildImportStructure(CimRevision revision, ProductConfiguration configuration) {
        StructureNode sourceTree = configuration != null
                ? configurationService.resolve150BOM(
                        revision.getSourceMaeObjectId(),
                        revision.getSourceMaeRevisionId(),
                        configuration.getId())
                : structureService.getStructureTree(
                        revision.getSourceMaeObjectId(),
                        revision.getSourceMaeRevisionId());

        return filterEligibleEbomTree(sourceTree, true);
    }

    private StructureNode filterEligibleEbomTree(StructureNode node, boolean keepCurrentNode) {
        if (node == null) {
            return null;
        }

        boolean eligibleCurrentNode = keepCurrentNode || isMaeCimEligibleObjectId(node.getObjectId());
        if (!eligibleCurrentNode) {
            return null;
        }

        StructureNode filtered = new StructureNode();
        filtered.setObjectId(node.getObjectId());
        filtered.setRevisionId(node.getRevisionId());
        filtered.setName(node.getName());
        filtered.setRevision(node.getRevision());
        filtered.setReleaseStatus(node.getReleaseStatus());
        filtered.setObjectType(node.getObjectType());
        filtered.setQuantity(node.getQuantity());
        filtered.setPositionNumber(node.getPositionNumber());
        filtered.setReleased(node.isReleased());
        filtered.setRelationId(node.getRelationId());
        filtered.setReferenceDesignator(node.getReferenceDesignator());
        filtered.setBmk(node.getBmk());
        filtered.setRelationType(node.getRelationType());
        filtered.setConfigRule(node.getConfigRule());
        filtered.setAlternativeGroup(node.getAlternativeGroup());
        filtered.setAlternativePriority(node.getAlternativePriority());

        for (StructureNode child : node.getChildren()) {
            StructureNode filteredChild = filterEligibleEbomTree(child, false);
            if (filteredChild != null) {
                filtered.getChildren().add(filteredChild);
            }
        }

        return filtered;
    }

    private int countNodes(StructureNode node) {
        if (node == null) {
            return 0;
        }
        int count = 1;
        for (StructureNode child : node.getChildren()) {
            count += countNodes(child);
        }
        return count;
    }

    private boolean isMaeCimEligibleObjectId(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            return false;
        }
        String prefix = objectId.substring(0, 2);
        return switch (prefix) {
            case "MA", "SY", "SU", "MO", "MU", "PT", "AX" -> true;
            default -> false;
        };
    }

    private void runValidation(CimRevision revision) {
        cimValidationResultRepository.deleteByCimRevisionRevisionId(revision.getRevisionId());

        List<CimValidationResult> results = new ArrayList<>();

        if (revision.getName() == null || revision.getName().isBlank()) {
            results.add(validation(revision, "CIM_NAME_REQUIRED", CimValidationSeverity.ERROR, "CIM name must not be empty", true));
        }

        CimAttributeSet attrs = cimAttributeSetRepository
                .findByCimRevisionRevisionId(revision.getRevisionId())
                .orElse(null);

        if (attrs == null) {
            results.add(validation(revision, "CIM_ATTR_REQUIRED", CimValidationSeverity.ERROR,
                    "CIM attribute set is missing", true));
        } else {
            if (attrs.getMaterial() == null || attrs.getMaterial().isBlank()) {
                results.add(validation(revision, "CIM_MATERIAL_WARN", CimValidationSeverity.WARNING,
                        "Material is empty", false));
            }
            if (attrs.getMass() != null && !attrs.getMass().isBlank() && !attrs.getMass().matches("\\d+(\\.\\d+)?")) {
                results.add(validation(revision, "CIM_MASS_FORMAT", CimValidationSeverity.ERROR,
                        "Mass must be numeric", true));
            }
        }

        List<CimStructureRelation> outgoing = cimStructureRelationRepository
                .findByParentCimRevisionRevisionId(revision.getRevisionId());
        if (outgoing.isEmpty()) {
            results.add(validation(revision, "CIM_STRUCTURE_WARN", CimValidationSeverity.WARNING,
                    "CIM revision has no child structure relations", false));
        }

        cimValidationResultRepository.saveAll(results);
    }

    private CimValidationResult validation(CimRevision revision,
                                           String ruleId,
                                           CimValidationSeverity severity,
                                           String message,
                                           boolean blocking) {
        CimValidationResult result = new CimValidationResult();
        result.setCimRevision(revision);
        result.setRuleId(ruleId);
        result.setSeverity(severity);
        result.setMessage(message);
        result.setBlocking(blocking);
        return result;
    }

    private CimManufacturingLink saveLink(CimRevision revision,
                                          CimManufacturingTargetType targetType,
                                          String targetId,
                                          CimImportMode importMode,
                          String user,
                          ProductConfiguration configuration,
                          int importedNodeCount) {
        CimManufacturingLink link = new CimManufacturingLink();
        link.setSourceCimRevision(revision);
        link.setTargetType(targetType);
        link.setTargetId(targetId);
        link.setConfigurationId(configuration != null ? configuration.getId() : null);
        link.setConfigurationName(configuration != null ? configuration.getName() : null);
        String frozenOptionTokens = buildFrozenOptionTokenSnapshot(configuration);
        link.setSelectedOptionTokens(frozenOptionTokens);
        link.setImportMode(importMode);
        link.setImportResult(CimImportResult.SUCCESS);
        link.setImportedAt(LocalDateTime.now());
        link.setImportedBy(user);
        String configPart = configuration != null
            ? " using configuration " + configuration.getName() + " (#" + configuration.getId() + ")"
            : " without configuration";
        String tokenPart = (frozenOptionTokens != null && !frozenOptionTokens.isBlank())
            ? " | tokens: " + frozenOptionTokens
            : "";
        link.setMessage("Imported from CIM " + revision.getCimObject().getId() + " rev " + revision.getRevision()
            + configPart + " with " + importedNodeCount + " structure nodes" + tokenPart);
        return cimManufacturingLinkRepository.save(link);
    }

    private String buildFrozenOptionTokenSnapshot(ProductConfiguration configuration) {
        if (configuration == null || configuration.getSelectedOptions() == null || configuration.getSelectedOptions().isEmpty()) {
            return null;
        }

        return configuration.getSelectedOptions().stream()
                .map(VariantOption::toRuleToken)
                .filter(token -> token != null && !token.isBlank())
                .sorted()
                .distinct()
                .collect(Collectors.joining(","));
    }

    private String incrementRevision(String revision) {
        int value = Integer.parseInt(revision);
        return String.format("%02d", value + 1);
    }

    private String buildContentHash(EBOMObject maeObject) {
        String seed = maeObject.getId() + "|" + defaultIfBlank(maeObject.getName(), "") + "|"
                + defaultIfBlank(maeObject.getDepartment(), "") + "|" + defaultIfBlank(maeObject.getMass(), "")
                + "|" + defaultIfBlank(maeObject.getMaterial(), "") + "|" + defaultIfBlank(maeObject.getSupplier(), "");
        return Integer.toHexString(seed.hashCode());
    }

    private String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
