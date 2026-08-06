// DATEI: src/main/java/com/peb/service/IDGeneratorService.java
package com.peb.service;

import com.peb.repository.AuxiliaryRepository;
import com.peb.repository.DesignRepository;
import com.peb.repository.DocumentRepository;
import com.peb.repository.DocumentTemplateRepository;
import com.peb.repository.DrawingRepository;
import com.peb.repository.EBOMModuleRepository;
import com.peb.repository.MachineRepository;
import com.peb.repository.MaterialRepository;
import com.peb.repository.PartRepository;
import com.peb.repository.SubmoduleRepository;
import com.peb.repository.SubsystemRepository;
import com.peb.repository.SupplierRepository;
import com.peb.repository.SystemRepository;
import com.peb.repository.product.ProductDataRepository;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IDGeneratorService {

    // ── Bestehende Repositories (unverändert) ─────────────────────
    private final DesignRepository     designRepository;
    private final DrawingRepository    drawingRepository;
    private final MachineRepository    machineRepository;
    private final SystemRepository     systemRepository;
    private final SubsystemRepository  subsystemRepository;
    private final EBOMModuleRepository ebomModuleRepository;
    private final SubmoduleRepository  submoduleRepository;
    private final PartRepository       partRepository;
    private final AuxiliaryRepository  auxiliaryRepository;

    // ── Produkt-Daten / Dokumente ──────────────────────────────────
    private final ProductDataRepository      productDataRepository;
    private final DocumentTemplateRepository documentTemplateRepository;
    private final DocumentRepository         documentRepository;

    // ── NEU: Materialmanagement ────────────────────────────────────
    private final MaterialRepository materialRepository;

    // ── NEU: Lieferantenmanagement ──────────────────────────────────
    private final SupplierRepository supplierRepository;

    @Transactional
    public synchronized String generateId(ObjectType objectType) {
        long sequence = getNextSequence(objectType);
        return objectType.formatId(sequence);
    }

    private long getNextSequence(ObjectType objectType) {
        Long max = switch (objectType) {
            case DESIGN       -> designRepository.findMaxSequence();
            case DRAWING      -> drawingRepository.findMaxSequence();
            case MACHINE      -> machineRepository.findMaxSequence();
            case SYSTEM       -> systemRepository.findMaxSequence();
            case SUBSYSTEM    -> subsystemRepository.findMaxSequence();
            case MODULE       -> ebomModuleRepository.findMaxSequence();
            case SUBMODULE    -> submoduleRepository.findMaxSequence();
            case PART         -> partRepository.findMaxSequence();
            case AUXILIARY    -> auxiliaryRepository.findMaxSequence();

            case PRODUCT_DATA ->
                    productDataRepository.findMaxSequence();
            case DOCUMENT_TEMPLATE ->
                    documentTemplateRepository.findMaxSequence();
            case DOCUMENT ->
                    documentRepository.findMaxSequence();

            case MATERIAL ->
                    materialRepository.findMaxSequence();

            case SUPPLIER ->
                    supplierRepository.findMaxSequence();
        };
        return (max != null ? max : 0L) + 1L;
    }
}
