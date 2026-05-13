package com.peb.service;

import com.peb.model.cad.Design;
import com.peb.model.ebom.*;
import com.peb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service für bidirektionale Synchronisation zwischen Design und eBOM.
 *
 * Regeln:
 *  - Mass:     Design  → eBOM  (Design ist führend)
 *  - Material: eBOM    → Design (eBOM ist führend)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SynchronizationService {

    private final DesignRepository      designRepository;
    private final MachineRepository     machineRepository;
    private final SystemRepository      systemRepository;
    private final SubsystemRepository   subsystemRepository;
    private final EBOMModuleRepository  ebomModuleRepository;
    private final SubmoduleRepository   submoduleRepository;
    private final PartRepository        partRepository;
    private final AuxiliaryRepository   auxiliaryRepository;

    // ── Public API ────────────────────────────────────────────

    /**
     * Mass: Design → eBOM
     * Wird aufgerufen wenn Design.mass geändert wird.
     */
    @Transactional
    public void syncMassFromDesign(String designId, String ebomId) {
        Design design = loadDesign(designId);
        EBOMObject ebomObject = getEBOMObject(ebomId);

        String designMass = design.getMass();
        String ebomMass   = ebomObject.getMass();

        // Nur syncen wenn Design-Mass gesetzt und unterschiedlich
        if (designMass != null && !designMass.isBlank()
                && !designMass.equals(ebomMass)) {

            ebomObject.setMass(designMass);
            saveEBOMObject(ebomObject);

            log.info("Sync Mass  Design[{}] → eBOM[{}]: '{}' → '{}'",
                     designId, ebomId, ebomMass, designMass);
        } else {
            log.debug("Sync Mass  Design[{}] → eBOM[{}]: keine Änderung (Design='{}', eBOM='{}')",
                      designId, ebomId, designMass, ebomMass);
        }
    }

    /**
     * Material: eBOM → Design
     * Wird aufgerufen wenn EBOMObject.material geändert wird.
     */
    @Transactional
    public void syncMaterialToDesign(String ebomId, String designId) {
        EBOMObject ebomObject  = getEBOMObject(ebomId);
        Design     design      = loadDesign(designId);

        String ebomMaterial   = ebomObject.getMaterial();
        String designMaterial = design.getMaterial();

        if (ebomMaterial != null && !ebomMaterial.isBlank()
                && !ebomMaterial.equals(designMaterial)) {

            design.setMaterial(ebomMaterial);
            designRepository.save(design);

            log.info("Sync Material  eBOM[{}] → Design[{}]: '{}' → '{}'",
                     ebomId, designId, designMaterial, ebomMaterial);
        } else {
            log.debug("Sync Material  eBOM[{}] → Design[{}]: keine Änderung (eBOM='{}', Design='{}')",
                      ebomId, designId, ebomMaterial, designMaterial);
        }
    }

    /**
     * Vollständige bidirektionale Synchronisation.
     * Wird beim erstmaligen Verlinken aufgerufen.
     */
    @Transactional
    public void performFullSync(String designId, String ebomId) {
        log.info("Full Sync: Design[{}] ↔ eBOM[{}]", designId, ebomId);
        syncMassFromDesign(designId, ebomId);
        syncMaterialToDesign(ebomId, designId);
    }

    /**
     * Wird aufgerufen wenn Design.mass direkt geändert wird.
     * Propagiert die neue Mass an ALLE verlinkten eBOM-Objekte.
     */
    @Transactional
    public void syncMassToAllLinkedEbomObjects(String designId) {
        Design design = loadDesign(designId);

        // Alle eBOM-Typen durchsuchen und verlinkte Objekte syncen
        machineRepository.findAll().stream()
            .filter(o -> designId.equals(o.getLinkedDesignId()))
            .forEach(o -> syncMassFromDesign(designId, o.getId()));

        systemRepository.findAll().stream()
            .filter(o -> designId.equals(o.getLinkedDesignId()))
            .forEach(o -> syncMassFromDesign(designId, o.getId()));

        subsystemRepository.findAll().stream()
            .filter(o -> designId.equals(o.getLinkedDesignId()))
            .forEach(o -> syncMassFromDesign(designId, o.getId()));

        ebomModuleRepository.findAll().stream()
            .filter(o -> designId.equals(o.getLinkedDesignId()))
            .forEach(o -> syncMassFromDesign(designId, o.getId()));

        submoduleRepository.findAll().stream()
            .filter(o -> designId.equals(o.getLinkedDesignId()))
            .forEach(o -> syncMassFromDesign(designId, o.getId()));

        partRepository.findAll().stream()
            .filter(o -> designId.equals(o.getLinkedDesignId()))
            .forEach(o -> syncMassFromDesign(designId, o.getId()));

        auxiliaryRepository.findAll().stream()
            .filter(o -> designId.equals(o.getLinkedDesignId()))
            .forEach(o -> syncMassFromDesign(designId, o.getId()));

        log.info("Mass-Propagation abgeschlossen für Design[{}], neue Mass='{}'",
                 designId, design.getMass());
    }

    // ── Private Helpers ───────────────────────────────────────

    private Design loadDesign(String designId) {
        return designRepository.findById(designId)
                .orElseThrow(() -> new RuntimeException(
                        "Design nicht gefunden: " + designId));
    }

    private EBOMObject getEBOMObject(String id) {
        String prefix = id.substring(0, 2);
        return switch (prefix) {
            case "MA" -> machineRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Machine not found: " + id));
            case "SY" -> systemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("System not found: " + id));
            case "SU" -> subsystemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Subsystem not found: " + id));
            case "MO" -> ebomModuleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Module not found: " + id));
            case "MU" -> submoduleRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Submodule not found: " + id));
            case "PT" -> partRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Part not found: " + id));
            case "AX" -> auxiliaryRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Auxiliary not found: " + id));
            default -> throw new RuntimeException(
                    "Unbekannter Objekttyp für ID: " + id);
        };
    }

    private void saveEBOMObject(EBOMObject ebomObject) {
        switch (ebomObject.getObjectType()) {
            case MACHINE    -> machineRepository.save((Machine) ebomObject);
            case SYSTEM     -> systemRepository.save((com.peb.model.ebom.System) ebomObject);
            case SUBSYSTEM  -> subsystemRepository.save((Subsystem) ebomObject);
            case MODULE     -> ebomModuleRepository.save((EBOMModule) ebomObject);
            case SUBMODULE  -> submoduleRepository.save((Submodule) ebomObject);
            case PART       -> partRepository.save((Part) ebomObject);
            case AUXILIARY  -> auxiliaryRepository.save((Auxiliary) ebomObject);
        }
    }
}