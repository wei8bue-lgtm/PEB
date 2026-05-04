package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.model.ebom.EBOMObject;
import com.peb.repository.DesignRepository;
import com.peb.repository.DesignRevisionRepository;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing Design objects
 */
@Service
@RequiredArgsConstructor
public class DesignService {

    private final DesignRepository designRepository;
    private final DesignRevisionRepository designRevisionRepository;
    private final IDGeneratorService idGeneratorService;
    private final SynchronizationService synchronizationService;
    private final EBOMService ebomService;

    @Transactional
    public Design createDesign(String name, String department) {
        Design design = new Design();
        design.setId(idGeneratorService.generateId(ObjectType.DESIGN));
        design.setName(name);
        design.setDepartment(department);

        design = designRepository.save(design);

        // Create initial revision
        DesignRevision revision = new DesignRevision();
        revision.setDesign(design);
        revision.setRevision("01");
        revision.setReleaseStatus(ReleaseStatus.WORKING);
        revision.syncFromParent();

        designRevisionRepository.save(revision);

        return design;
    }

    @Transactional(readOnly = true)
    public Design getDesign(String id) {
        return designRepository.findById(id)
            .orElseThrow(() -> new ObjectNotFoundException("Design not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Design> getAllDesigns() {
        return designRepository.findAll();
    }

    @Transactional
    public Design updateDesign(String id, String name, String department, String mass, String material) {
        Design design = getDesign(id);
        
        String oldMass = design.getMass();
        
        design.setName(name);
        design.setDepartment(department);
        design.setMass(mass);
        design.setMaterial(material);

        // Sync to latest working revision
        DesignRevision latestRevision = design.getLatestRevision();
        if (latestRevision != null && latestRevision.getReleaseStatus() == ReleaseStatus.WORKING) {
            latestRevision.syncFromParent();
            designRevisionRepository.save(latestRevision);
        }

        Design savedDesign = designRepository.save(design);

        // Sync mass to linked eBOM objects if mass changed
        if (mass != null && !mass.equals(oldMass)) {
            syncMassToLinkedEBOMObjects(id);
        }

        return savedDesign;
    }

    @Transactional
    public void deleteDesign(String id) {
        Design design = getDesign(id);
        designRepository.delete(design);
    }

    @Transactional(readOnly = true)
    public List<Design> searchDesigns(String searchTerm) {
        return designRepository.findByNameContainingIgnoreCase(searchTerm);
    }

    /**
     * Sync mass from this design to all linked eBOM objects
     */
    @Transactional
    public void syncMassToLinkedEBOMObjects(String designId) {
        List<EBOMObject> allEBOMObjects = ebomService.getAllEBOMObjects();
        
        for (EBOMObject ebom : allEBOMObjects) {
            if (designId.equals(ebom.getLinkedDesignId())) {
                synchronizationService.syncMassFromDesign(designId, ebom.getId());
            }
        }
    }
}