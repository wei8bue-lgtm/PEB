package com.peb.service;

import com.peb.dto.MaterialComplianceDTO;
import com.peb.dto.MaterialDTO;
import com.peb.dto.MaterialRevisionDTO;
import com.peb.dto.MaterialSupplierDTO;
import com.peb.exception.ObjectNotFoundException;
import com.peb.model.material.Material;
import com.peb.model.material.MaterialCompliance;
import com.peb.model.material.MaterialRevision;
import com.peb.model.material.MaterialSupplier;
import com.peb.repository.MaterialComplianceRepository;
import com.peb.repository.MaterialRepository;
import com.peb.repository.MaterialRevisionRepository;
import com.peb.repository.MaterialSupplierRepository;
import com.peb.service.search.SearchIndexService;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.peb.model.material.MaterialStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Kernservice für Materialmanagement.
 * Verwaltet Materialstammdaten, Revisionen, Compliance (REACH/RoHS/Konfliktmineralien)
 * und Lieferantenbeziehungen (AVL).
 *
 * Die ECLASS-Klassifizierung von Materialien läuft weiterhin über den
 * bestehenden {@code EclassService} (objectId + ObjectType.MATERIAL) –
 * hier bewusst nicht dupliziert.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialRevisionRepository materialRevisionRepository;
    private final MaterialComplianceRepository materialComplianceRepository;
    private final MaterialSupplierRepository materialSupplierRepository;
    private final IDGeneratorService idGeneratorService;
    private final SearchIndexService searchIndexService; // NEU: zentrale Suche

    // ── Stammdaten ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MaterialDTO> findAll() {
        return materialRepository.findAll().stream()
                .map(MaterialDTO::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MaterialDTO getMaterial(String id) {
        Material m = materialRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Material nicht gefunden: " + id));
        return MaterialDTO.from(m);
    }

    @Transactional(readOnly = true)
    public List<MaterialDTO> search(String term) {
        if (term == null || term.isBlank()) return List.of();
        return materialRepository.searchByTerm(term.trim()).stream()
                .map(MaterialDTO::from).collect(Collectors.toList());
    }

    /**
     * Legt ein neues Material an. Vergibt automatisch eine ID (MT########)
     * und erzeugt die initiale Revision "A" im Status WORKING.
     */
    @Transactional
    public MaterialDTO createMaterial(MaterialDTO dto) {
        Material m = new Material();
        m.setId(idGeneratorService.generateId(ObjectType.MATERIAL));
        applyFields(m, dto);
        m.setCreatedBy(currentUser());
        m.setLastModifiedBy(currentUser());
        Material saved = materialRepository.save(m);

        createRevision(saved.getId(), "A", "Erstanlage", ReleaseStatus.WORKING);

        log.info("Material {} angelegt: {}", saved.getId(), saved.getName());

        // NEU: zentrale Suche aktualisieren
        // "saved" enthaelt die Revision bereits in-memory (siehe Fix in createRevision(),
        // das dieselbe Objektinstanz ueber den Hibernate-Identity-Cache aktualisiert)
        searchIndexService.index(ObjectType.MATERIAL, saved);

        return MaterialDTO.from(saved);
    }

    @Transactional
    public MaterialDTO updateMaterial(String id, MaterialDTO dto) {
        Material m = materialRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Material nicht gefunden: " + id));
        applyFields(m, dto);
        m.setLastModifiedBy(currentUser());
        Material saved = materialRepository.save(m);
        log.info("Material {} aktualisiert", id);

        // NEU: zentrale Suche aktualisieren
        searchIndexService.index(ObjectType.MATERIAL, saved);

        return MaterialDTO.from(saved);
    }

    @Transactional
    public void deleteMaterial(String id) {
        materialRepository.deleteById(id);
        log.info("Material {} gelöscht", id);

        // NEU: aus zentraler Suche entfernen
        searchIndexService.remove(ObjectType.MATERIAL, id);
    }

    private void applyFields(Material m, MaterialDTO dto) {
        m.setName(dto.getName());
        m.setDepartment(dto.getDepartment());
        m.setMaterialGroup(dto.getMaterialGroup());
        m.setSpecification(dto.getSpecification());
        m.setUnitOfMeasure(dto.getUnitOfMeasure());
        m.setDensityKgM3(dto.getDensityKgM3());
        m.setTensileStrengthMpa(dto.getTensileStrengthMpa());
        m.setElasticModulusGpa(dto.getElasticModulusGpa());
        m.setMeltingPointC(dto.getMeltingPointC());
        m.setThermalConductivityWMk(dto.getThermalConductivityWMk());
        m.setDescription(dto.getDescription());
        if (dto.getStatus() != null) m.setStatus(dto.getStatus());
    }

    // ── Revisionen ───────────────────────────────────────────────

    @Transactional
    public MaterialRevisionDTO createRevision(String materialId, String revision,
                                               String changeDescription, ReleaseStatus status) {
        Material m = materialRepository.findById(materialId)
                .orElseThrow(() -> new ObjectNotFoundException("Material nicht gefunden: " + materialId));

        MaterialRevision rev = new MaterialRevision();
        rev.setMaterial(m);
        rev.setRevision(revision);
        rev.setChangeDescription(changeDescription);
        rev.setReleaseStatus(status != null ? status : ReleaseStatus.WORKING);
        rev.setCreatedBy(currentUser());
        MaterialRevision saved = materialRevisionRepository.save(rev);
        m.getRevisions().add(saved); // NEU: haelt In-Memory-Graph konsistent (wichtig fuer createMaterial -> Suchindexierung)
        return MaterialRevisionDTO.from(saved);
    }

    @Transactional
    public MaterialRevisionDTO releaseRevision(Long revisionId) {
        MaterialRevision rev = materialRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ObjectNotFoundException("Revision nicht gefunden: " + revisionId));

        if (rev.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new IllegalStateException(
                    "Nur Revisionen im Status WORKING können freigegeben werden (aktueller Status: "
                            + rev.getReleaseStatus() + ")");
        }

        String materialId = rev.getMaterial().getId();

        // Pro Material darf nur eine Revision RELEASED sein – alle anderen
        // bisher freigegebenen Revisionen werden beim Freigeben der neuen
        // Revision automatisch auf OBSOLETE gesetzt.
        materialRevisionRepository.findByMaterialIdOrderByCreatedDateAsc(materialId).stream()
                .filter(other -> !other.getId().equals(revisionId))
                .filter(other -> other.getReleaseStatus() == ReleaseStatus.RELEASED)
                .forEach(other -> {
                    other.setReleaseStatus(ReleaseStatus.OBSOLETE);
                    materialRevisionRepository.save(other);
                    log.info("Material-Revision {} ({}) automatisch auf OBSOLETE gesetzt (abgelöst durch {})",
                            materialId, other.getRevision(), rev.getRevision());
                });

        rev.setReleaseStatus(ReleaseStatus.RELEASED);
        rev.setReleasedBy(currentUser());
        rev.setReleasedDate(LocalDateTime.now());
        MaterialRevision saved = materialRevisionRepository.save(rev);
        log.info("Material-Revision {} ({}) freigegeben", materialId, rev.getRevision());
        return MaterialRevisionDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MaterialRevisionDTO> getRevisions(String materialId) {
        return materialRevisionRepository.findByMaterialIdOrderByCreatedDateAsc(materialId).stream()
                .map(MaterialRevisionDTO::from).collect(Collectors.toList());
    }

    // ── Compliance ───────────────────────────────────────────────

    @Transactional
    public MaterialComplianceDTO saveCompliance(String materialId, MaterialComplianceDTO dto) {
        Material m = materialRepository.findById(materialId)
                .orElseThrow(() -> new ObjectNotFoundException("Material nicht gefunden: " + materialId));

        MaterialCompliance compliance = materialComplianceRepository.findByMaterialId(materialId)
                .orElseGet(() -> {
                    MaterialCompliance c = new MaterialCompliance();
                    c.setMaterial(m);
                    return c;
                });

        compliance.setReachSvhc(dto.getReachSvhc());
        compliance.setReachSvhcSubstances(dto.getReachSvhcSubstances());
        compliance.setReachCheckedDate(dto.getReachCheckedDate());
        compliance.setRohsCompliant(dto.getRohsCompliant());
        compliance.setRohsCheckedDate(dto.getRohsCheckedDate());
        compliance.setConflictMineralsStatus(dto.getConflictMineralsStatus());
        compliance.setComplianceDocumentPath(dto.getComplianceDocumentPath());
        compliance.setNextReviewDate(dto.getNextReviewDate());
        compliance.setNotes(dto.getNotes());
        compliance.setLastModifiedBy(currentUser());

        MaterialCompliance saved = materialComplianceRepository.save(compliance);
        log.info("Compliance-Daten für Material {} aktualisiert", materialId);
        return MaterialComplianceDTO.from(saved);
    }

    /** Materialien, deren Compliance-Prüfung fällig oder überfällig ist. */
    @Transactional(readOnly = true)
    public List<MaterialDTO> findComplianceDueForReview() {
        return materialComplianceRepository.findByNextReviewDateBefore(LocalDate.now()).stream()
                .map(c -> MaterialDTO.from(c.getMaterial()))
                .collect(Collectors.toList());
    }

    // ── Lieferanten (AVL) ────────────────────────────────────────

    @Transactional
    public MaterialSupplierDTO addSupplier(String materialId, MaterialSupplierDTO dto) {
        Material m = materialRepository.findById(materialId)
                .orElseThrow(() -> new ObjectNotFoundException("Material nicht gefunden: " + materialId));

        MaterialSupplier s = new MaterialSupplier();
        s.setMaterial(m);
        applySupplierFields(s, dto);
        MaterialSupplier saved = materialSupplierRepository.save(s);
        return MaterialSupplierDTO.from(saved);
    }

    @Transactional
    public MaterialSupplierDTO updateSupplier(Long supplierId, MaterialSupplierDTO dto) {
        MaterialSupplier s = materialSupplierRepository.findById(supplierId)
                .orElseThrow(() -> new ObjectNotFoundException("Lieferant nicht gefunden: " + supplierId));
        applySupplierFields(s, dto);
        return MaterialSupplierDTO.from(materialSupplierRepository.save(s));
    }

    private void applySupplierFields(MaterialSupplier s, MaterialSupplierDTO dto) {
        s.setSupplierName(dto.getSupplierName());
        s.setSupplierPartNumber(dto.getSupplierPartNumber());
        s.setPrice(dto.getPrice());
        s.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : "EUR");
        s.setLeadTimeDays(dto.getLeadTimeDays());
        s.setMinOrderQuantity(dto.getMinOrderQuantity());
        s.setApproved(dto.getApproved());
        s.setPreferred(dto.getPreferred());
        s.setNotes(dto.getNotes());
    }
	
	// ── Auswahlliste für Verknüpfungen (z.B. Design) ────────────────

	/** Materialien, die freigegeben (RELEASED-Revision vorhanden) und Status ACTIVE sind. */
	@Transactional(readOnly = true)
	public List<MaterialDTO> getReleasedActiveMaterials() {
		return materialRepository.findAll().stream()
				.filter(m -> m.getReleasedRevision() != null)
				.filter(m -> m.getStatus() == MaterialStatus.ACTIVE)
				.map(MaterialDTO::from)
				.collect(Collectors.toList());
	}

    @Transactional
    public void removeSupplier(Long supplierId) {
        materialSupplierRepository.deleteById(supplierId);
    }

    @Transactional(readOnly = true)
    public List<MaterialSupplierDTO> getSuppliers(String materialId) {
        return materialSupplierRepository.findByMaterialIdOrderByPreferredDescApprovedDesc(materialId).stream()
                .map(MaterialSupplierDTO::from).collect(Collectors.toList());
    }

    // ── Hilfsmethoden ────────────────────────────────────────────

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }
}
