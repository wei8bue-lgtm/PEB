// src/main/java/com/peb/service/product/ManufacturingBOMService.java
package com.peb.service.product;

import com.peb.model.product.*;
import com.peb.repository.product.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManufacturingBOMService {

    private final ManufacturingBOMRepository         mfgRepo;
    private final ManufacturingBOMRevisionRepository mfgRevRepo;
    private final ManufacturingOperationRepository   opRepo;

    // ── Erstellen mit initialer Revision ─────────────────────────
    @Transactional
    public ManufacturingBOM create(ManufacturingBOM bom) {

        ManufacturingBOM saved = mfgRepo.save(bom);

        // Initiale Revision 01 automatisch erstellen
        ManufacturingBOMRevision initRev =
                new ManufacturingBOMRevision();
        initRev.setManufacturingBOM(saved);
        initRev.setRevision("01");
        initRev.setStatus(ManufacturingStatus.WORKING);
        initRev.setCreatedBy(currentUser());
        mfgRevRepo.save(initRev);

        log.info("ManufacturingBOM '{}' (ID: {}) erstellt "
                + "mit Revision 01 (WORKING)",
                saved.getName(), saved.getId());
        return saved;
    }

    // ── Vollständiges Laden ───────────────────────────────────────
    @Transactional(readOnly = true)
    public ManufacturingBOM findByIdWithAllDetails(Long id) {

        ManufacturingBOM bom = mfgRepo
                .findByIdWithProductBOMs(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ManufacturingBOM nicht gefunden: " + id));

        List<ManufacturingOperation> operations =
                opRepo.findByManufacturingBOMIdOrderBySequenceNumber(id);

        bom.setOperations(operations);
        return bom;
    }

    // ── Nur mit Operationen ───────────────────────────────────────
    @Transactional(readOnly = true)
    public ManufacturingBOM findByIdWithOperationsOnly(Long id) {

        ManufacturingBOM bom = mfgRepo.findByIdOnly(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ManufacturingBOM nicht gefunden: " + id));

        List<ManufacturingOperation> operations =
                opRepo.findByManufacturingBOMIdOrderBySequenceNumber(id);

        bom.setOperations(operations);
        return bom;
    }

    @Transactional(readOnly = true)
    public Optional<ManufacturingBOM> findByIdWithOperations(
            Long id) {
        try {
            return Optional.of(findByIdWithOperationsOnly(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // ── Hilfsmethode ─────────────────────────────────────────────
    private String currentUser() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}