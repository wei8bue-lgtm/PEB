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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing Design objects
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DesignService {

    private final DesignRepository           designRepository;
    private final DesignRevisionRepository   designRevisionRepository;
    private final IDGeneratorService         idGeneratorService;
    private final SynchronizationService     synchronizationService;
    private final EBOMService                ebomService;

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
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Design not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Design> getAllDesigns() {
        return designRepository.findAll();
    }

    /**
     * Design aktualisieren.
     *
     * Sync-Reihenfolge ist entscheidend:
     * 1. Alte Werte merken
     * 2. Design speichern  ← ERST DANN in DB vorhanden
     * 3. Sync ausführen    ← liest neue Werte aus DB
     */
    @Transactional
    public Design updateDesign(String id, String name, String department,
                               String mass, String material, String childView) {
        Design design = getDesign(id);

        // 1. Alte Werte merken (VOR setMass/setMaterial)
        String oldMass     = design.getMass();
        String oldMaterial = design.getMaterial();

        // 2. Felder setzen
        design.setName(name);
        design.setDepartment(department);
        design.setMass(mass);
        design.setMaterial(material);
        design.setChildView(childView);

        // Revision synchronisieren
        DesignRevision latestRevision = design.getLatestRevision();
        if (latestRevision != null
                && latestRevision.getReleaseStatus() == ReleaseStatus.WORKING) {
            latestRevision.syncFromParent();
            designRevisionRepository.save(latestRevision);
        }

        // 3. ERST speichern – dann Sync (damit DB aktuell ist)
        Design savedDesign = designRepository.save(design);
        designRepository.flush(); // sicherstellen dass Werte in DB sind

        // 4. Mass geändert → alle verlinkten eBOM-Objekte aktualisieren
        if (mass != null && !mass.equals(oldMass)) {
            log.info("Design[{}] mass geändert: '{}' → '{}' → Sync zu eBOM",
                     id, oldMass, mass);
            syncMassToLinkedEBOMObjects(id);
        }

        // 5. Material geändert → Info loggen
        //    (Material-Sync läuft eBOM → Design, nicht andersrum)
        if (material != null && !material.equals(oldMaterial)) {
            log.info("Design[{}] material geändert: '{}' → '{}'",
                     id, oldMaterial, material);
        }

        return savedDesign;
    }

    /**
     * Überladene Methode für Rückwärtskompatibilität
     * (falls andere Stellen updateDesign ohne childView aufrufen)
     */
    @Transactional
    public Design updateDesign(String id, String name, String department,
                               String mass, String material) {
        return updateDesign(id, name, department, mass, material, null);
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
     * Mass aus diesem Design an alle verlinkten eBOM-Objekte propagieren.
     * Wird nach designRepository.flush() aufgerufen damit DB-Werte aktuell.
     */
    @Transactional
    public void syncMassToLinkedEBOMObjects(String designId) {
        List<EBOMObject> allEBOMObjects = ebomService.getAllEBOMObjects();

        long count = allEBOMObjects.stream()
                .filter(ebom -> designId.equals(ebom.getLinkedDesignId()))
                .peek(ebom -> synchronizationService
                        .syncMassFromDesign(designId, ebom.getId()))
                .count();

        log.info("Mass-Sync Design[{}] → {} verlinkte eBOM-Objekte", designId, count);
    }
}