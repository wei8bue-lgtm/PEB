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
import com.peb.model.material.Material;
import com.peb.repository.MaterialRepository;
import com.peb.service.search.SearchIndexService;
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
	private final MaterialRepository         materialRepository; 
	private final SearchIndexService         searchIndexService; // NEU: zentrale Suche

	@Transactional
	public Design createDesign(String name, String department, String mass, String materialId) {
		Design design = new Design();
		design.setId(idGeneratorService.generateId(ObjectType.DESIGN));
		design.setName(name);
		design.setDepartment(department);
		design.setMass(mass);

		if (materialId != null && !materialId.isBlank()) {
			Material material = materialRepository.findById(materialId)
					.orElseThrow(() -> new ObjectNotFoundException(
							"Material nicht gefunden: " + materialId));
			design.setLinkedMaterialId(materialId);
			design.setMaterial(material.getName());
		}

		design = designRepository.save(design);

		// Create initial revision
		DesignRevision revision = new DesignRevision();
		revision.setDesign(design);
		revision.setRevision("01");
		revision.setReleaseStatus(ReleaseStatus.WORKING);
		revision.syncFromParent();
		designRevisionRepository.save(revision);
		design.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort greift

		// NEU: zentrale Suche aktualisieren
		searchIndexService.index(ObjectType.DESIGN, design);

		return design;
	}

	/**
	 * Überladene Methode für Rückwärtskompatibilität
	 * (falls andere Stellen createDesign ohne Mass/Material aufrufen)
	 */
	@Transactional
	public Design createDesign(String name, String department) {
		return createDesign(name, department, null, null);
	}

    @Transactional(readOnly = true, noRollbackFor = com.peb.exception.ObjectNotFoundException.class)
    public Design getDesign(String id) {
        return designRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Design not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Design> getAllDesigns() {
        return designRepository.findAll();
    }
	
	@Transactional(readOnly = true)
	public DesignRevision getRevision(Long revisionId) {
		return designRevisionRepository.findById(revisionId)
				.orElseThrow(() -> new ObjectNotFoundException(
						"Design revision not found: " + revisionId));
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
							   String mass, String materialId, String childView) {
		Design design = getDesign(id);

		String oldMass             = design.getMass();
		String oldLinkedMaterialId = design.getLinkedMaterialId();

		design.setName(name);
		design.setDepartment(department);
		design.setMass(mass);
		design.setChildView(childView);

		// ── Material-Verknüpfung auflösen (Design ist jetzt führend) ──
		if (materialId != null && !materialId.isBlank()) {
			Material material = materialRepository.findById(materialId)
					.orElseThrow(() -> new ObjectNotFoundException(
							"Material nicht gefunden: " + materialId));
			design.setLinkedMaterialId(materialId);
			design.setMaterial(material.getName());
		} else {
			design.setLinkedMaterialId(null);
			design.setMaterial(null);
		}

		DesignRevision latestRevision = design.getLatestRevision();
		if (latestRevision != null
				&& latestRevision.getReleaseStatus() == ReleaseStatus.WORKING) {
			latestRevision.syncFromParent();
			designRevisionRepository.save(latestRevision);
		}

		Design savedDesign = designRepository.save(design);
		designRepository.flush();

		if (mass != null && !mass.equals(oldMass)) {
			log.info("Design[{}] mass geändert: '{}' → '{}' → Sync zu eBOM", id, oldMass, mass);
			syncMassToLinkedEBOMObjects(id);
		}

		// ── NEU: Material-Link geändert → eBOM-Sync ────────────────────
		if (!java.util.Objects.equals(materialId, oldLinkedMaterialId)) {
			log.info("Design[{}] Material-Link geändert: '{}' → '{}' → Sync zu eBOM",
					 id, oldLinkedMaterialId, materialId);
			syncMaterialToLinkedEBOMObjects(id);
		}

		// NEU: zentrale Suche aktualisieren
		searchIndexService.index(ObjectType.DESIGN, savedDesign);

		return savedDesign;
	}

	@Transactional
	public Design updateDesign(String id, String name, String department,
							   String mass, String materialId) {
		return updateDesign(id, name, department, mass, materialId, null);
	}
	
	@Transactional
	public void syncMaterialToLinkedEBOMObjects(String designId) {
		List<EBOMObject> allEBOMObjects = ebomService.getAllEBOMObjects();

		long count = allEBOMObjects.stream()
				.filter(ebom -> designId.equals(ebom.getLinkedDesignId()))
				.peek(ebom -> synchronizationService.syncMaterialFromDesign(designId, ebom.getId()))
				.count();

		log.info("Material-Sync Design[{}] → {} verlinkte eBOM-Objekte", designId, count);
	}

    @Transactional
    public void deleteDesign(String id) {
        Design design = getDesign(id);
        designRepository.delete(design);

        // NEU: aus zentraler Suche entfernen
        searchIndexService.remove(ObjectType.DESIGN, id);
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