package com.peb.service;

import com.peb.model.cad.Design;
import com.peb.model.ebom.*;
import com.peb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for bi-directional synchronization between Design and eBOM objects
 * - Mass syncs from Design to eBOM
 * - Material syncs from eBOM to Design
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SynchronizationService {

    private final DesignRepository designRepository;
    private final MachineRepository machineRepository;
    private final SystemRepository systemRepository;
    private final SubsystemRepository subsystemRepository;
    private final EBOMModuleRepository ebomModuleRepository;
    private final SubmoduleRepository submoduleRepository;
    private final PartRepository partRepository;
    private final AuxiliaryRepository auxiliaryRepository;

    /**
     * Sync mass from Design to eBOM object
     */
    @Transactional
    public void syncMassFromDesign(String designId, String ebomId) {
        Design design = designRepository.findById(designId)
            .orElseThrow(() -> new RuntimeException("Design not found: " + designId));
        
        EBOMObject ebomObject = getEBOMObject(ebomId);
        
        if (design.getMass() != null && !design.getMass().isEmpty()) {
            String oldMass = ebomObject.getMass();
            ebomObject.setMass(design.getMass());
            saveEBOMObject(ebomObject);
            
            log.info("Synced mass from Design {} to eBOM {}: {} -> {}", 
                     designId, ebomId, oldMass, design.getMass());
        }
    }

    /**
     * Sync material from eBOM to Design object
     */
    @Transactional
    public void syncMaterialToDesign(String ebomId, String designId) {
        EBOMObject ebomObject = getEBOMObject(ebomId);
        Design design = designRepository.findById(designId)
            .orElseThrow(() -> new RuntimeException("Design not found: " + designId));
        
        if (ebomObject.getMaterial() != null && !ebomObject.getMaterial().isEmpty()) {
            String oldMaterial = design.getMaterial();
            design.setMaterial(ebomObject.getMaterial());
            designRepository.save(design);
            
            log.info("Synced material from eBOM {} to Design {}: {} -> {}", 
                     ebomId, designId, oldMaterial, ebomObject.getMaterial());
        }
    }

    /**
     * Perform full bi-directional sync when linking objects
     */
    @Transactional
    public void performFullSync(String designId, String ebomId) {
        syncMassFromDesign(designId, ebomId);
        syncMaterialToDesign(ebomId, designId);
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
            default -> throw new RuntimeException("Unknown object type for ID: " + id);
        };
    }

    private void saveEBOMObject(EBOMObject ebomObject) {
        switch (ebomObject.getObjectType()) {
            case MACHINE -> machineRepository.save((Machine) ebomObject);
            case SYSTEM -> systemRepository.save((com.peb.model.ebom.System) ebomObject);
            case SUBSYSTEM -> subsystemRepository.save((Subsystem) ebomObject);
            case MODULE -> ebomModuleRepository.save((EBOMModule) ebomObject);
            case SUBMODULE -> submoduleRepository.save((Submodule) ebomObject);
            case PART -> partRepository.save((Part) ebomObject);
            case AUXILIARY -> auxiliaryRepository.save((Auxiliary) ebomObject);
        }
    }
}