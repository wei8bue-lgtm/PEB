package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.model.cad.Design;
import com.peb.model.ebom.*;
import com.peb.repository.*;
import com.peb.service.search.SearchIndexService;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing eBOM objects
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EBOMService {

    private final IDGeneratorService idGeneratorService;
    private final MachineRepository machineRepository;
    private final SystemRepository systemRepository;
    private final SubsystemRepository subsystemRepository;
    private final EBOMModuleRepository ebomModuleRepository;
    private final SubmoduleRepository submoduleRepository;
    private final PartRepository partRepository;
    private final AuxiliaryRepository auxiliaryRepository;
    private final DesignRepository designRepository;
    private final SynchronizationService synchronizationService;
    private final SearchIndexService searchIndexService; // NEU: zentrale Suche
    
    // Revision repositories
    private final MachineRevisionRepository machineRevisionRepository;
    private final SystemRevisionRepository systemRevisionRepository;
    private final SubsystemRevisionRepository subsystemRevisionRepository;
    private final EBOMModuleRevisionRepository ebomModuleRevisionRepository;
    private final SubmoduleRevisionRepository submoduleRevisionRepository;
    private final PartRevisionRepository partRevisionRepository;
    private final AuxiliaryRevisionRepository auxiliaryRevisionRepository;

    @Transactional
    public EBOMObject createEBOMObject(ObjectType objectType, String name, String department,
                                       String mass, String material, String supplier, String linkedDesignId) {
        String id = idGeneratorService.generateId(objectType);
        
        // Validate linked design if provided
        if (linkedDesignId != null && !linkedDesignId.isEmpty()) {
            Design design = designRepository.findById(linkedDesignId)
                .orElseThrow(() -> new ObjectNotFoundException("Design not found: " + linkedDesignId));
            
            // Sync mass from design if not provided
            if (mass == null || mass.isEmpty()) {
                mass = design.getMass();
            }
        }
        
        final String finalMass = mass;
        final String finalLinkedDesignId = linkedDesignId;
        
        EBOMObject ebomObject = switch (objectType) {
            case MACHINE -> {
                Machine machine = new Machine();
                machine.setId(id);
                machine.setName(name);
                machine.setDepartment(department);
                machine.setMass(finalMass);
                machine.setMaterial(material);
                machine.setSupplier(supplier);
                machine.setLinkedDesignId(finalLinkedDesignId);
                machine = machineRepository.save(machine);
                
                // Create initial revision
                MachineRevision revision = new MachineRevision();
                revision.setMachine(machine);
                revision.setRevision("01");
                revision.setReleaseStatus(ReleaseStatus.WORKING);
                revision.syncFromParent();
                machineRevisionRepository.save(revision);
                machine.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort die neue Revision sieht (fuer sofortige Suchindexierung)
                
                yield machine;
            }
            case SYSTEM -> {
                com.peb.model.ebom.System system = new com.peb.model.ebom.System();
                system.setId(id);
                system.setName(name);
                system.setDepartment(department);
                system.setMass(finalMass);
                system.setMaterial(material);
                system.setSupplier(supplier);
                system.setLinkedDesignId(finalLinkedDesignId);
                system = systemRepository.save(system);
                
                // Create initial revision
                SystemRevision revision = new SystemRevision();
                revision.setSystem(system);
                revision.setRevision("01");
                revision.setReleaseStatus(ReleaseStatus.WORKING);
                revision.syncFromParent();
                systemRevisionRepository.save(revision);
                system.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort die neue Revision sieht (fuer sofortige Suchindexierung)
                
                yield system;
            }
            case SUBSYSTEM -> {
                Subsystem subsystem = new Subsystem();
                subsystem.setId(id);
                subsystem.setName(name);
                subsystem.setDepartment(department);
                subsystem.setMass(finalMass);
                subsystem.setMaterial(material);
                subsystem.setSupplier(supplier);
                subsystem.setLinkedDesignId(finalLinkedDesignId);
                subsystem = subsystemRepository.save(subsystem);
                
                // Create initial revision
                SubsystemRevision revision = new SubsystemRevision();
                revision.setSubsystem(subsystem);
                revision.setRevision("01");
                revision.setReleaseStatus(ReleaseStatus.WORKING);
                revision.syncFromParent();
                subsystemRevisionRepository.save(revision);
                subsystem.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort die neue Revision sieht (fuer sofortige Suchindexierung)
                
                yield subsystem;
            }
            case MODULE -> {
                EBOMModule module = new EBOMModule();
                module.setId(id);
                module.setName(name);
                module.setDepartment(department);
                module.setMass(finalMass);
                module.setMaterial(material);
                module.setSupplier(supplier);
                module.setLinkedDesignId(finalLinkedDesignId);
                module = ebomModuleRepository.save(module);
                
                // Create initial revision
                EBOMModuleRevision revision = new EBOMModuleRevision();
                revision.setModule(module);
                revision.setRevision("01");
                revision.setReleaseStatus(ReleaseStatus.WORKING);
                revision.syncFromParent();
                ebomModuleRevisionRepository.save(revision);
                module.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort die neue Revision sieht (fuer sofortige Suchindexierung)
                
                yield module;
            }
            case SUBMODULE -> {
                Submodule submodule = new Submodule();
                submodule.setId(id);
                submodule.setName(name);
                submodule.setDepartment(department);
                submodule.setMass(finalMass);
                submodule.setMaterial(material);
                submodule.setSupplier(supplier);
                submodule.setLinkedDesignId(finalLinkedDesignId);
                submodule = submoduleRepository.save(submodule);
                
                // Create initial revision
                SubmoduleRevision revision = new SubmoduleRevision();
                revision.setSubmodule(submodule);
                revision.setRevision("01");
                revision.setReleaseStatus(ReleaseStatus.WORKING);
                revision.syncFromParent();
                submoduleRevisionRepository.save(revision);
                submodule.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort die neue Revision sieht (fuer sofortige Suchindexierung)
                
                yield submodule;
            }
            case PART -> {
                Part part = new Part();
                part.setId(id);
                part.setName(name);
                part.setDepartment(department);
                part.setMass(finalMass);
                part.setMaterial(material);
                part.setSupplier(supplier);
                part.setLinkedDesignId(finalLinkedDesignId);
                part = partRepository.save(part);
                
                // Create initial revision
                PartRevision revision = new PartRevision();
                revision.setPart(part);
                revision.setRevision("01");
                revision.setReleaseStatus(ReleaseStatus.WORKING);
                revision.syncFromParent();
                partRevisionRepository.save(revision);
                part.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort die neue Revision sieht (fuer sofortige Suchindexierung)
                
                yield part;
            }
            case AUXILIARY -> {
                Auxiliary auxiliary = new Auxiliary();
                auxiliary.setId(id);
                auxiliary.setName(name);
                auxiliary.setDepartment(department);
                auxiliary.setMass(finalMass);
                auxiliary.setMaterial(material);
                auxiliary.setSupplier(supplier);
                auxiliary.setLinkedDesignId(finalLinkedDesignId);
                auxiliary = auxiliaryRepository.save(auxiliary);
                
                // Create initial revision
                AuxiliaryRevision revision = new AuxiliaryRevision();
                revision.setAuxiliary(auxiliary);
                revision.setRevision("01");
                revision.setReleaseStatus(ReleaseStatus.WORKING);
                revision.syncFromParent();
                auxiliaryRevisionRepository.save(revision);
                auxiliary.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort die neue Revision sieht (fuer sofortige Suchindexierung)
                
                yield auxiliary;
            }
            default -> throw new IllegalArgumentException("Unsupported object type: " + objectType);
        };

        log.info("Created eBOM object {} with initial revision 01", id);

        // Perform bi-directional sync if design is linked
        if (linkedDesignId != null && !linkedDesignId.isEmpty()) {
            synchronizationService.performFullSync(linkedDesignId, ebomObject.getId());
        }

        // NEU: zentrale Suche aktualisieren
        searchIndexService.index(objectType, ebomObject);

        return ebomObject;
    }

    @Transactional(readOnly = true, noRollbackFor = com.peb.exception.ObjectNotFoundException.class)
    public EBOMObject getEBOMObject(String id) {
        String prefix = id.substring(0, 2);
        
        return switch (prefix) {
            case "MA" -> machineRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Machine not found: " + id));
            case "SY" -> systemRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("System not found: " + id));
            case "SU" -> subsystemRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Subsystem not found: " + id));
            case "MO" -> ebomModuleRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Module not found: " + id));
            case "MU" -> submoduleRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Submodule not found: " + id));
            case "PT" -> partRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Part not found: " + id));
            case "AX" -> auxiliaryRepository.findById(id)
                    .orElseThrow(() -> new ObjectNotFoundException("Auxiliary not found: " + id));
            default -> throw new ObjectNotFoundException("Unknown object type for ID: " + id);
        };
    }

@Transactional
public EBOMObject updateEBOMObject(String id, String name, String department,
                                   String mass, String material, String supplier,
                                   String linkedDesignId) {
    EBOMObject ebomObject = getEBOMObject(id);

    String oldLinkedDesignId = ebomObject.getLinkedDesignId();
    String oldMaterial       = ebomObject.getMaterial();
    String oldMass           = ebomObject.getMass();

    ebomObject.setName(name);
    ebomObject.setDepartment(department);
    ebomObject.setMass(mass);
    ebomObject.setMaterial(material);
    ebomObject.setSupplier(supplier);
    ebomObject.setLinkedDesignId(linkedDesignId);

    EBOMObject savedObject = switch (ebomObject.getObjectType()) {
        case MACHINE    -> machineRepository.save((Machine) ebomObject);
        case SYSTEM     -> systemRepository.save((com.peb.model.ebom.System) ebomObject);
        case SUBSYSTEM  -> subsystemRepository.save((Subsystem) ebomObject);
        case MODULE     -> ebomModuleRepository.save((EBOMModule) ebomObject);
        case SUBMODULE  -> submoduleRepository.save((Submodule) ebomObject);
        case PART       -> partRepository.save((Part) ebomObject);
        case AUXILIARY  -> auxiliaryRepository.save((Auxiliary) ebomObject);
        default -> throw new IllegalArgumentException("Unsupported object type");
    };

	// ── Sync-Logik ────────────────────────────────────────────
	if (linkedDesignId != null && !linkedDesignId.isBlank()) {

		if (!linkedDesignId.equals(oldLinkedDesignId)) {
			log.info("Design-Link geändert → Full Sync: Design[{}] ↔ eBOM[{}]",
					 linkedDesignId, id);
			synchronizationService.performFullSync(linkedDesignId, id);

		} else {
			// Material manuell am eBOM geändert → Design hat Priorität → zurücksetzen
			// (Design ist jetzt führend für Material, analog zu Mass)
			if (material != null && !material.equals(oldMaterial)) {                 // ← geändert
				log.info("eBOM Material manuell geändert → Design[{}] überschreibt eBOM[{}]",  // ← geändert
						 linkedDesignId, id);
				synchronizationService.syncMaterialFromDesign(linkedDesignId, id);   // ← geändert
			}

			// Mass manuell geändert → Design.mass hat Priorität → zurücksetzen
			if (mass != null && !mass.equals(oldMass)) {
				log.info("eBOM Mass manuell geändert → Design[{}] überschreibt eBOM[{}]",
						 linkedDesignId, id);
				synchronizationService.syncMassFromDesign(linkedDesignId, id);
			}
		}
	}

    // NEU: zentrale Suche aktualisieren
    searchIndexService.index(savedObject.getObjectType(), savedObject);

    return savedObject;
}

    @Transactional
    public void deleteEBOMObject(String id) {
        EBOMObject ebomObject = getEBOMObject(id);
        ObjectType objectType = ebomObject.getObjectType();

        switch (objectType) {
            case MACHINE -> machineRepository.delete((Machine) ebomObject);
            case SYSTEM -> systemRepository.delete((com.peb.model.ebom.System) ebomObject);
            case SUBSYSTEM -> subsystemRepository.delete((Subsystem) ebomObject);
            case MODULE -> ebomModuleRepository.delete((EBOMModule) ebomObject);
            case SUBMODULE -> submoduleRepository.delete((Submodule) ebomObject);
            case PART -> partRepository.delete((Part) ebomObject);
            case AUXILIARY -> auxiliaryRepository.delete((Auxiliary) ebomObject);
        }

        // NEU: aus zentraler Suche entfernen
        searchIndexService.remove(objectType, id);
    }

    @Transactional(readOnly = true)
    public List<Machine> getAllMachines() {
        return machineRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<com.peb.model.ebom.System> getAllSystems() {
        return systemRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Subsystem> getAllSubsystems() {
        return subsystemRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<EBOMModule> getAllModules() {
        return ebomModuleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Submodule> getAllSubmodules() {
        return submoduleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Part> getAllParts() {
        return partRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Auxiliary> getAllAuxiliaries() {
        return auxiliaryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<EBOMObject> getAllEBOMObjects() {
        List<EBOMObject> allObjects = new ArrayList<>();
        allObjects.addAll(getAllMachines());
        allObjects.addAll(getAllSystems());
        allObjects.addAll(getAllSubsystems());
        allObjects.addAll(getAllModules());
        allObjects.addAll(getAllSubmodules());
        allObjects.addAll(getAllParts());
        allObjects.addAll(getAllAuxiliaries());
        return allObjects;
    }
}