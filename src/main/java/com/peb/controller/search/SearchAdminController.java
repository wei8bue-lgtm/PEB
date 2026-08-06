package com.peb.controller.search;

import com.peb.model.cad.Design;
import com.peb.model.cad.Drawing;
import com.peb.model.document.Document;
import com.peb.model.document.DocumentTemplate;
import com.peb.model.ebom.Auxiliary;
import com.peb.model.ebom.EBOMModule;
import com.peb.model.ebom.Machine;
import com.peb.model.ebom.Part;
import com.peb.model.ebom.Subsystem;
import com.peb.model.ebom.Submodule;
import com.peb.model.material.Material;
import com.peb.model.product.ProductData;
import com.peb.model.supplier.Supplier;
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
import com.peb.service.search.SearchIndexService;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-Only Endpunkt, um den Elasticsearch-Index einmalig aus dem
 * DB-Bestand zu befüllen (Rollout) oder bei Bedarf komplett neu
 * aufzubauen (z.B. nach Schema-Änderungen am SearchDocument).
 *
 * BEWUSST kein automatischer Reindex beim Server-Start: Bei mehreren
 * tausend Objekten würde das den Boot-Vorgang unnötig verlangsamen bzw.
 * blockieren. Stattdessen: einmal manuell nach dem Deployment triggern.
 *
 * Deckt alle 14 Objekttypen aus ObjectType ab.
 */
@Slf4j
@RestController
@RequestMapping("/api/search/admin")
@RequiredArgsConstructor
public class SearchAdminController {

    private final SearchIndexService searchIndexService;

    // eBOM-Typen (über EBOMService verwaltet, hier aber direkt per Repository gelesen)
    private final PartRepository partRepository;
    private final MachineRepository machineRepository;
    private final SystemRepository systemRepository;
    private final SubsystemRepository subsystemRepository;
    private final EBOMModuleRepository ebomModuleRepository;
    private final SubmoduleRepository submoduleRepository;
    private final AuxiliaryRepository auxiliaryRepository;

    // CAD
    private final DesignRepository designRepository;
    private final DrawingRepository drawingRepository;

    // Dokumente
    private final DocumentRepository documentRepository;
    private final DocumentTemplateRepository documentTemplateRepository;

    // Stammdaten
    private final MaterialRepository materialRepository;
    private final SupplierRepository supplierRepository;

    // Produktdaten
    private final ProductDataRepository productDataRepository;

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> reindexAll() {
        int total = 0;

        total += reindex(ObjectType.PART, partRepository.findAll());
        total += reindex(ObjectType.MACHINE, machineRepository.findAll());
        total += reindex(ObjectType.SYSTEM, systemRepository.findAll());
        total += reindex(ObjectType.SUBSYSTEM, subsystemRepository.findAll());
        total += reindex(ObjectType.MODULE, ebomModuleRepository.findAll());
        total += reindex(ObjectType.SUBMODULE, submoduleRepository.findAll());
        total += reindex(ObjectType.AUXILIARY, auxiliaryRepository.findAll());

        total += reindex(ObjectType.DESIGN, designRepository.findAll());
        total += reindex(ObjectType.DRAWING, drawingRepository.findAll());

        total += reindex(ObjectType.DOCUMENT, documentRepository.findAll());
        total += reindex(ObjectType.DOCUMENT_TEMPLATE, documentTemplateRepository.findAll());

        total += reindex(ObjectType.MATERIAL, materialRepository.findAll());
        total += reindex(ObjectType.SUPPLIER, supplierRepository.findAll());

        total += reindex(ObjectType.PRODUCT_DATA, productDataRepository.findAll());

        log.info("Voller Reindex abgeschlossen: {} Objekte über 14 Typen hinweg verarbeitet.", total);
        return ResponseEntity.ok("Reindex abgeschlossen: " + total + " Objekte über 14 Objekttypen verarbeitet.");
    }

    private <T> int reindex(ObjectType type, List<T> entities) {
        searchIndexService.reindexAll(type, entities);
        return entities.size();
    }
}
