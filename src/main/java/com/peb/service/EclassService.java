package com.peb.service;

import com.peb.dto.EclassClassDTO;
import com.peb.dto.EclassClassificationDTO;
import com.peb.dto.EclassSimilarResultDTO;
import com.peb.exception.ObjectNotFoundException;
import com.peb.model.eclass.EclassClass;
import com.peb.model.eclass.EclassProperty;
import com.peb.model.eclass.ObjectClassification;
import com.peb.model.eclass.ObjectPropertyValue;
import com.peb.model.cad.Design;
import com.peb.model.ebom.*;
import com.peb.repository.*;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Kernservice für ECLASS-Klassifizierung.
 * Verwaltet Klassen, Merkmale und Objekt-Klassifizierungen.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EclassService {

    private final EclassClassRepository        eclassClassRepository;
    private final EclassPropertyRepository     eclassPropertyRepository;
    private final ObjectClassificationRepository objectClassificationRepository;
    private final ObjectPropertyValueRepository  objectPropertyValueRepository;

    // Für Objekt-Name-Auflösung bei ähnlichen Objekten
    private final DesignRepository    designRepository;
    private final PartRepository      partRepository;
    private final MachineRepository   machineRepository;
    private final SystemRepository    systemRepository;
    private final SubsystemRepository subsystemRepository;
    private final EBOMModuleRepository ebomModuleRepository;
    private final SubmoduleRepository  submoduleRepository;
    private final AuxiliaryRepository  auxiliaryRepository;

    // ── Objekttyp-spezifische Suchbeschränkungen ────────────────────────────

    /**
     * ECLASS-Segmente, in denen Materialien klassifiziert werden dürfen.
     * 35 = Werkstoffe/Halbzeuge (vormals Segment 24), 31 = Chemische Grundstoffe/Kunststoffe.
     * Gilt ausschließlich für {@link ObjectType#MATERIAL} – alle anderen
     * Objekttypen sind von dieser Einschränkung nicht betroffen.
     */
    private static final Set<String> MATERIAL_ALLOWED_SEGMENTS = Set.of("35", "31");

    private static final int SEARCH_RESULT_LIMIT = 20;

    /** Größerer Fetch-Puffer, falls anschließend serverseitig nach Segment gefiltert wird,
     *  damit trotz Filterung genügend Treffer übrig bleiben. */
    private static final int SEARCH_FETCH_LIMIT_WHEN_FILTERED = 200;

    // ── Katalog-Abfragen ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EclassClassDTO> searchClasses(String term) {
        return searchClasses(term, null);
    }

    /**
     * Volltextsuche im ECLASS-Katalog, optional beschränkt auf die für einen
     * bestimmten Objekttyp zulässigen Segmente.
     *
     * @param term       Suchbegriff (Name oder Code)
     * @param objectType optional – wenn {@link ObjectType#MATERIAL}, werden nur
     *                   Treffer aus den Segmenten 35-* / 31-* zurückgegeben.
     *                   Für alle anderen Objekttypen (oder null) unverändertes Verhalten.
     */
    @Transactional(readOnly = true)
    public List<EclassClassDTO> searchClasses(String term, ObjectType objectType) {
        if (term == null || term.isBlank()) return List.of();

        boolean restrictToMaterialSegments = objectType == ObjectType.MATERIAL;
        int fetchSize = restrictToMaterialSegments ? SEARCH_FETCH_LIMIT_WHEN_FILTERED : SEARCH_RESULT_LIMIT;

        List<EclassClassDTO> results = eclassClassRepository
                .searchByTermTop20(term.trim(), PageRequest.of(0, fetchSize))
                .stream()
                .map(EclassClassDTO::from)
                .collect(Collectors.toList());

        if (restrictToMaterialSegments) {
            results = results.stream()
                    .filter(c -> MATERIAL_ALLOWED_SEGMENTS.contains(c.getSegment()))
                    .limit(SEARCH_RESULT_LIMIT)
                    .collect(Collectors.toList());
        }

        return results;
    }

    @Transactional(readOnly = true)
    public List<EclassClassDTO> getSegments() {
        return eclassClassRepository.findByClassLevelOrderByCode(1)
                .stream().map(EclassClassDTO::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EclassClassDTO> getMainGroups(String segment) {
        // JS übergibt ggf. den vollen Code "27-00-00-00" — wir brauchen nur "27" (2 Chars)
        String seg = segment.length() > 2 ? segment.substring(0, 2) : segment;
        return eclassClassRepository.findBySegmentAndClassLevelOrderByCode(seg, 2)
                .stream().map(EclassClassDTO::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EclassClassDTO> getGroups(String mainGroup) {
        // JS übergibt ggf. "27-37-00-00" — wir brauchen nur "27-37" (5 Chars)
        String mg = mainGroup.length() > 5 ? mainGroup.substring(0, 5) : mainGroup;
        return eclassClassRepository.findByMainGroupAndClassLevelOrderByCode(mg, 3)
                .stream().map(EclassClassDTO::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EclassClassDTO> getCommodities(String groupCode) {
        // JS übergibt ggf. "27-37-04-00" — wir brauchen nur "27-37-04" (8 Chars)
        String gc = groupCode.length() > 8 ? groupCode.substring(0, 8) : groupCode;
        return eclassClassRepository.findByGroupCodeAndClassLevelOrderByCode(gc, 4)
                .stream().map(EclassClassDTO::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EclassClassDTO getEclassClass(Long id) {
        EclassClass cls = eclassClassRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("ECLASS-Klasse nicht gefunden: " + id));
        return EclassClassDTO.from(cls);
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableVersions() {
        return eclassClassRepository.findDistinctVersions();
    }

    @Transactional(readOnly = true)
    public long getCommodityCount() {
        return eclassClassRepository.countCommodities();
    }

    @Transactional
    public int fixHierarchyData() {
        return eclassClassRepository.fixHierarchyFields();
    }

    // ── Objekt-Klassifizierung ──────────────────────────────────────────────

    /**
     * Gibt die bestehende Klassifizierung eines Objekts zurück, oder null.
     */
    @Transactional(readOnly = true)
    public EclassClassificationDTO getClassification(String objectId, ObjectType objectType) {
        return objectClassificationRepository
                .findByObjectIdAndObjectType(objectId, objectType)
                .map(EclassClassificationDTO::from)
                .orElse(null);
    }

    /**
     * Klassifiziert ein PEB-Objekt mit einer ECLASS-Klasse.
     * Legt eine neue Klassifizierung an oder aktualisiert die bestehende.
     *
     * @param objectId       PEB-Objekt-ID
     * @param objectType     Objekttyp (DESIGN, PART, …)
     * @param eclassClassId  ID der ECLASS-Klasse
     * @param propertyValues Map propertyId → Wert
     */
    @Transactional
    public EclassClassificationDTO classify(String objectId, ObjectType objectType,
                                             Long eclassClassId,
                                             Map<Long, String> propertyValues) {
        EclassClass eclassClass = eclassClassRepository.findById(eclassClassId)
                .orElseThrow(() -> new ObjectNotFoundException("ECLASS-Klasse nicht gefunden: " + eclassClassId));

        ObjectClassification oc = objectClassificationRepository
                .findByObjectIdAndObjectType(objectId, objectType)
                .orElseGet(() -> {
                    ObjectClassification newOc = new ObjectClassification();
                    newOc.setObjectId(objectId);
                    newOc.setObjectType(objectType);
                    return newOc;
                });

        oc.setEclassClass(eclassClass);
        oc.setCreatedBy(currentUser());

        // Bestehende Merkmalswerte löschen und neu setzen
        oc.getPropertyValues().clear();
        objectClassificationRepository.save(oc);

        if (propertyValues != null && !propertyValues.isEmpty()) {
            for (Map.Entry<Long, String> entry : propertyValues.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) continue;
                EclassProperty property = eclassPropertyRepository.findById(entry.getKey())
                        .orElse(null);
                if (property == null) continue;

                ObjectPropertyValue pv = new ObjectPropertyValue();
                pv.setClassification(oc);
                pv.setProperty(property);
                pv.setValue(entry.getValue().trim());
                oc.getPropertyValues().add(pv);
            }
        }

        ObjectClassification saved = objectClassificationRepository.save(oc);
        log.info("Objekt {} ({}) klassifiziert als ECLASS {}", objectId, objectType, eclassClass.getCode());
        return EclassClassificationDTO.from(saved);
    }

    /**
     * Entfernt die Klassifizierung eines Objekts.
     */
    @Transactional
    public void removeClassification(String objectId, ObjectType objectType) {
        objectClassificationRepository
                .findByObjectIdAndObjectType(objectId, objectType)
                .ifPresent(oc -> {
                    objectClassificationRepository.delete(oc);
                    log.info("ECLASS-Klassifizierung von {} ({}) entfernt", objectId, objectType);
                });
    }

    // ── Wiederverwendungssuche ──────────────────────────────────────────────

    /**
     * Sucht alle PEB-Objekte mit der gleichen ECLASS-Klasse (außer dem Referenzobjekt).
     */
    @Transactional(readOnly = true)
    public List<EclassSimilarResultDTO> findSimilar(String objectId, ObjectType objectType) {
        ObjectClassification ref = objectClassificationRepository
                .findByObjectIdAndObjectType(objectId, objectType)
                .orElse(null);
        if (ref == null || ref.getEclassClass() == null) return List.of();

        Long classId = ref.getEclassClass().getId();
        return objectClassificationRepository.findSimilar(classId, objectId)
                .stream()
                .map(oc -> {
                    String name = resolveObjectName(oc.getObjectId(), oc.getObjectType());
                    String dept = resolveObjectDepartment(oc.getObjectId(), oc.getObjectType());
                    return new EclassSimilarResultDTO(
                            oc.getObjectId(),
                            oc.getObjectType(),
                            name,
                            dept,
                            oc.getEclassClass().getCode(),
                            oc.getEclassClass().getName() != null
                                    ? oc.getEclassClass().getName()
                                    : oc.getEclassClass().getNameEn()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Sucht alle klassifizierten Objekte eines bestimmten ECLASS-Codes.
     */
    @Transactional(readOnly = true)
    public List<EclassSimilarResultDTO> findByEclassCode(String code) {
        return objectClassificationRepository.findByEclassCode(code)
                .stream()
                .map(oc -> {
                    String name = resolveObjectName(oc.getObjectId(), oc.getObjectType());
                    String dept = resolveObjectDepartment(oc.getObjectId(), oc.getObjectType());
                    return new EclassSimilarResultDTO(
                            oc.getObjectId(),
                            oc.getObjectType(),
                            name,
                            dept,
                            oc.getEclassClass().getCode(),
                            oc.getEclassClass().getName() != null
                                    ? oc.getEclassClass().getName()
                                    : oc.getEclassClass().getNameEn()
                    );
                })
                .collect(Collectors.toList());
    }

    // ── Hilfsmethoden ──────────────────────────────────────────────────────

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }

    private String resolveObjectName(String objectId, ObjectType type) {
        if (objectId == null || type == null) return objectId;
        try {
            return switch (type) {
                case DESIGN    -> designRepository.findById(objectId).map(Design::getName).orElse(objectId);
                case PART      -> partRepository.findById(objectId).map(Part::getName).orElse(objectId);
                case MACHINE   -> machineRepository.findById(objectId).map(Machine::getName).orElse(objectId);
                case SYSTEM    -> systemRepository.findById(objectId).map(com.peb.model.ebom.System::getName).orElse(objectId);
                case SUBSYSTEM -> subsystemRepository.findById(objectId).map(Subsystem::getName).orElse(objectId);
                case MODULE    -> ebomModuleRepository.findById(objectId).map(EBOMModule::getName).orElse(objectId);
                case SUBMODULE -> submoduleRepository.findById(objectId).map(Submodule::getName).orElse(objectId);
                case AUXILIARY -> auxiliaryRepository.findById(objectId).map(Auxiliary::getName).orElse(objectId);
                default        -> objectId;
            };
        } catch (Exception e) {
            return objectId;
        }
    }

    private String resolveObjectDepartment(String objectId, ObjectType type) {
        if (objectId == null || type == null) return null;
        try {
            return switch (type) {
                case DESIGN    -> designRepository.findById(objectId).map(Design::getDepartment).orElse(null);
                case PART      -> partRepository.findById(objectId).map(Part::getDepartment).orElse(null);
                case MACHINE   -> machineRepository.findById(objectId).map(Machine::getDepartment).orElse(null);
                case SYSTEM    -> systemRepository.findById(objectId).map(com.peb.model.ebom.System::getDepartment).orElse(null);
                case SUBSYSTEM -> subsystemRepository.findById(objectId).map(Subsystem::getDepartment).orElse(null);
                case MODULE    -> ebomModuleRepository.findById(objectId).map(EBOMModule::getDepartment).orElse(null);
                case SUBMODULE -> submoduleRepository.findById(objectId).map(Submodule::getDepartment).orElse(null);
                case AUXILIARY -> auxiliaryRepository.findById(objectId).map(Auxiliary::getDepartment).orElse(null);
                default        -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }
}
