package com.peb.service;

import com.peb.dto.SupplierDTO;
import com.peb.dto.SupplierRevisionDTO;
import com.peb.exception.ObjectNotFoundException;
import com.peb.model.supplier.Supplier;
import com.peb.model.supplier.SupplierRevision;
import com.peb.model.supplier.SupplierStatus;
import com.peb.repository.SupplierRepository;
import com.peb.repository.SupplierRevisionRepository;
import com.peb.service.search.SearchIndexService;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Kernservice für Lieferantenmanagement. Aufbau bewusst analog zu
 * MaterialService: Stammdaten + Revisionshistorie mit Freigabe-Workflow
 * (WORKING → RELEASED, alte RELEASED-Revision wird automatisch OBSOLETE).
 *
 * Eigenständig gehalten, d.h. NICHT verknüpft mit MaterialSupplier
 * (der bestehenden AVL-Verknüpfungstabelle Material↔Lieferant).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierRevisionRepository supplierRevisionRepository;
    private final IDGeneratorService idGeneratorService;
    private final SearchIndexService searchIndexService; // NEU: zentrale Suche

    // ── Stammdaten ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SupplierDTO> findAll() {
        return supplierRepository.findAll().stream()
                .map(SupplierDTO::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SupplierDTO getSupplier(String id) {
        Supplier s = supplierRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Lieferant nicht gefunden: " + id));
        return SupplierDTO.from(s);
    }

    @Transactional(readOnly = true)
    public List<SupplierDTO> search(String term) {
        if (term == null || term.isBlank()) return List.of();
        return supplierRepository.searchByTerm(term.trim()).stream()
                .map(SupplierDTO::from).collect(Collectors.toList());
    }

    /**
     * Legt einen neuen Lieferanten an. Vergibt automatisch eine ID (MF########)
     * und erzeugt die initiale Revision "A" im Status WORKING.
     */
    @Transactional
    public SupplierDTO createSupplier(SupplierDTO dto) {
        Supplier s = new Supplier();
        s.setId(idGeneratorService.generateId(ObjectType.SUPPLIER));
        applyFields(s, dto);
        s.setCreatedBy(currentUser());
        s.setLastModifiedBy(currentUser());
        Supplier saved = supplierRepository.save(s);

        createRevision(saved.getId(), "A", "Erstanlage", ReleaseStatus.WORKING);

        log.info("Lieferant {} angelegt: {}", saved.getId(), saved.getName());

        // NEU: zentrale Suche aktualisieren
        // "saved" enthaelt die Revision bereits in-memory (siehe Fix in createRevision(),
        // das dieselbe Objektinstanz ueber den Hibernate-Identity-Cache aktualisiert)
        searchIndexService.index(ObjectType.SUPPLIER, saved);

        return SupplierDTO.from(saved);
    }

    @Transactional
    public SupplierDTO updateSupplier(String id, SupplierDTO dto) {
        Supplier s = supplierRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Lieferant nicht gefunden: " + id));
        applyFields(s, dto);
        s.setLastModifiedBy(currentUser());
        Supplier saved = supplierRepository.save(s);
        log.info("Lieferant {} aktualisiert", id);

        // NEU: zentrale Suche aktualisieren
        searchIndexService.index(ObjectType.SUPPLIER, saved);

        return SupplierDTO.from(saved);
    }

    @Transactional
    public void deleteSupplier(String id) {
        supplierRepository.deleteById(id);
        log.info("Lieferant {} gelöscht", id);

        // NEU: aus zentraler Suche entfernen
        searchIndexService.remove(ObjectType.SUPPLIER, id);
    }

    private void applyFields(Supplier s, SupplierDTO dto) {
        s.setName(dto.getName());
        s.setStreet(dto.getStreet());
        s.setZipCode(dto.getZipCode());
        s.setCity(dto.getCity());
        s.setCountry(dto.getCountry());
        s.setContactPerson(dto.getContactPerson());
        s.setContactEmail(dto.getContactEmail());
        s.setContactPhone(dto.getContactPhone());
        s.setWebsite(dto.getWebsite());
        s.setNotes(dto.getNotes());
        if (dto.getStatus() != null) s.setStatus(dto.getStatus());
    }

    // ── Revisionen ───────────────────────────────────────────────

    @Transactional
    public SupplierRevisionDTO createRevision(String supplierId, String revision,
                                               String changeDescription, ReleaseStatus status) {
        Supplier s = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ObjectNotFoundException("Lieferant nicht gefunden: " + supplierId));

        SupplierRevision rev = new SupplierRevision();
        rev.setSupplier(s);
        rev.setRevision(revision);
        rev.setChangeDescription(changeDescription);
        rev.setReleaseStatus(status != null ? status : ReleaseStatus.WORKING);
        rev.setCreatedBy(currentUser());
        SupplierRevision saved = supplierRevisionRepository.save(rev);
        // NEU: haelt In-Memory-Graph konsistent (wichtig fuer createSupplier -> Suchindexierung).
        // Achtung: anders als bei Material hat Supplier.revisions KEINEN
        // Default-Initializer (= new ArrayList<>()) - bei einem frisch
        // angelegten Supplier ist die Liste sonst null.
        if (s.getRevisions() == null) {
            s.setRevisions(new java.util.ArrayList<>());
        }
        s.getRevisions().add(saved);
        return SupplierRevisionDTO.from(saved);
    }

    @Transactional
    public SupplierRevisionDTO releaseRevision(Long revisionId) {
        SupplierRevision rev = supplierRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ObjectNotFoundException("Revision nicht gefunden: " + revisionId));

        if (rev.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new IllegalStateException(
                    "Nur Revisionen im Status WORKING können freigegeben werden (aktueller Status: "
                            + rev.getReleaseStatus() + ")");
        }

        String supplierId = rev.getSupplier().getId();

        // Pro Lieferant darf nur eine Revision RELEASED sein – alle anderen
        // bisher freigegebenen Revisionen werden beim Freigeben der neuen
        // Revision automatisch auf OBSOLETE gesetzt.
        supplierRevisionRepository.findBySupplierIdOrderByCreatedDateAsc(supplierId).stream()
                .filter(other -> !other.getId().equals(revisionId))
                .filter(other -> other.getReleaseStatus() == ReleaseStatus.RELEASED)
                .forEach(other -> {
                    other.setReleaseStatus(ReleaseStatus.OBSOLETE);
                    supplierRevisionRepository.save(other);
                    log.info("Lieferanten-Revision {} ({}) automatisch auf OBSOLETE gesetzt (abgelöst durch {})",
                            supplierId, other.getRevision(), rev.getRevision());
                });

        rev.setReleaseStatus(ReleaseStatus.RELEASED);
        rev.setReleasedBy(currentUser());
        rev.setReleasedDate(LocalDateTime.now());
        SupplierRevision saved = supplierRevisionRepository.save(rev);
        log.info("Lieferanten-Revision {} ({}) freigegeben", supplierId, rev.getRevision());
        return SupplierRevisionDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SupplierRevisionDTO> getRevisions(String supplierId) {
        return supplierRevisionRepository.findBySupplierIdOrderByCreatedDateAsc(supplierId).stream()
                .map(SupplierRevisionDTO::from).collect(Collectors.toList());
    }

    // ── Auswahlliste für Verknüpfungen (z.B. künftige AVL-Umstellung) ──

    /** Lieferanten, die freigegeben (RELEASED-Revision vorhanden) und Status ACTIVE sind. */
    @Transactional(readOnly = true)
    public List<SupplierDTO> getReleasedActiveSuppliers() {
        return supplierRepository.findAll().stream()
                .filter(s -> s.getReleasedRevision() != null)
                .filter(s -> s.getStatus() == SupplierStatus.ACTIVE)
                .map(SupplierDTO::from)
                .collect(Collectors.toList());
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
