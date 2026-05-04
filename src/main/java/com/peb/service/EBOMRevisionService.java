package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.exception.WorkflowException;
import com.peb.model.ebom.*;
import com.peb.repository.*;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing eBOM revisions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EBOMRevisionService {

    private final MachineRepository machineRepository;
    private final SystemRepository systemRepository;
    private final SubsystemRepository subsystemRepository;
    private final EBOMModuleRepository ebomModuleRepository;
    private final SubmoduleRepository submoduleRepository;
    private final PartRepository partRepository;
    private final AuxiliaryRepository auxiliaryRepository;
    
    private final MachineRevisionRepository machineRevisionRepository;
    private final SystemRevisionRepository systemRevisionRepository;
    private final SubsystemRevisionRepository subsystemRevisionRepository;
    private final EBOMModuleRevisionRepository ebomModuleRevisionRepository;
    private final SubmoduleRevisionRepository submoduleRevisionRepository;
    private final PartRevisionRepository partRevisionRepository;
    private final AuxiliaryRevisionRepository auxiliaryRevisionRepository;
    
    private final EmailService emailService;

    @Transactional
    public EBOMRevision reviseEBOMObject(String objectId, String username) {
        String prefix = objectId.substring(0, 2);
        
        return switch (prefix) {
            case "MA" -> reviseMachine(objectId, username);
            case "SY" -> reviseSystem(objectId, username);
            case "SU" -> reviseSubsystem(objectId, username);
            case "MO" -> reviseModule(objectId, username);
            case "MU" -> reviseSubmodule(objectId, username);
            case "PT" -> revisePart(objectId, username);
            case "AX" -> reviseAuxiliary(objectId, username);
            default -> throw new ObjectNotFoundException("Unknown object type: " + prefix);
        };
    }

    private MachineRevision reviseMachine(String machineId, String username) {
        Machine machine = machineRepository.findById(machineId)
            .orElseThrow(() -> new ObjectNotFoundException("Machine not found: " + machineId));
        
        MachineRevision latestRevision = machineRevisionRepository
            .findFirstByMachineIdOrderByRevisionDesc(machineId)
            .orElseThrow(() -> new WorkflowException("No revision found"));
        
        if (latestRevision.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new WorkflowException("Cannot revise: latest revision is not released");
        }
        
        try {
            MachineRevision newRevision = new MachineRevision();
            newRevision.setMachine(machine);
            copyRevisionData(latestRevision, newRevision);
            newRevision.setRevision(incrementRevision(latestRevision.getRevision()));
            newRevision.setReleaseStatus(ReleaseStatus.WORKING);
            
            MachineRevision saved = machineRevisionRepository.save(newRevision);
            emailService.sendRevisionSuccessEmail(username, machineId, saved.getRevision());
            log.info("Created new revision {} for Machine {}", saved.getRevision(), machineId);
            
            return saved;
        } catch (Exception e) {
            emailService.sendRevisionFailureEmail(username, machineId, e.getMessage());
            throw new WorkflowException("Failed to create revision: " + e.getMessage(), e);
        }
    }

    private SystemRevision reviseSystem(String systemId, String username) {
        com.peb.model.ebom.System system = systemRepository.findById(systemId)
            .orElseThrow(() -> new ObjectNotFoundException("System not found: " + systemId));
        
        SystemRevision latestRevision = systemRevisionRepository
            .findFirstBySystemIdOrderByRevisionDesc(systemId)
            .orElseThrow(() -> new WorkflowException("No revision found"));
        
        if (latestRevision.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new WorkflowException("Cannot revise: latest revision is not released");
        }
        
        try {
            SystemRevision newRevision = new SystemRevision();
            newRevision.setSystem(system);
            copyRevisionData(latestRevision, newRevision);
            newRevision.setRevision(incrementRevision(latestRevision.getRevision()));
            newRevision.setReleaseStatus(ReleaseStatus.WORKING);
            
            SystemRevision saved = systemRevisionRepository.save(newRevision);
            emailService.sendRevisionSuccessEmail(username, systemId, saved.getRevision());
            
            return saved;
        } catch (Exception e) {
            emailService.sendRevisionFailureEmail(username, systemId, e.getMessage());
            throw new WorkflowException("Failed to create revision: " + e.getMessage(), e);
        }
    }

    private SubsystemRevision reviseSubsystem(String subsystemId, String username) {
        Subsystem subsystem = subsystemRepository.findById(subsystemId)
            .orElseThrow(() -> new ObjectNotFoundException("Subsystem not found: " + subsystemId));
        
        SubsystemRevision latestRevision = subsystemRevisionRepository
            .findFirstBySubsystemIdOrderByRevisionDesc(subsystemId)
            .orElseThrow(() -> new WorkflowException("No revision found"));
        
        if (latestRevision.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new WorkflowException("Cannot revise: latest revision is not released");
        }
        
        try {
            SubsystemRevision newRevision = new SubsystemRevision();
            newRevision.setSubsystem(subsystem);
            copyRevisionData(latestRevision, newRevision);
            newRevision.setRevision(incrementRevision(latestRevision.getRevision()));
            newRevision.setReleaseStatus(ReleaseStatus.WORKING);
            
            SubsystemRevision saved = subsystemRevisionRepository.save(newRevision);
            emailService.sendRevisionSuccessEmail(username, subsystemId, saved.getRevision());
            
            return saved;
        } catch (Exception e) {
            emailService.sendRevisionFailureEmail(username, subsystemId, e.getMessage());
            throw new WorkflowException("Failed to create revision: " + e.getMessage(), e);
        }
    }

    private EBOMModuleRevision reviseModule(String moduleId, String username) {
        EBOMModule module = ebomModuleRepository.findById(moduleId)
            .orElseThrow(() -> new ObjectNotFoundException("Module not found: " + moduleId));
        
        EBOMModuleRevision latestRevision = ebomModuleRevisionRepository
            .findFirstByModuleIdOrderByRevisionDesc(moduleId)
            .orElseThrow(() -> new WorkflowException("No revision found"));
        
        if (latestRevision.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new WorkflowException("Cannot revise: latest revision is not released");
        }
        
        try {
            EBOMModuleRevision newRevision = new EBOMModuleRevision();
            newRevision.setModule(module);
            copyRevisionData(latestRevision, newRevision);
            newRevision.setRevision(incrementRevision(latestRevision.getRevision()));
            newRevision.setReleaseStatus(ReleaseStatus.WORKING);
            
            EBOMModuleRevision saved = ebomModuleRevisionRepository.save(newRevision);
            emailService.sendRevisionSuccessEmail(username, moduleId, saved.getRevision());
            
            return saved;
        } catch (Exception e) {
            emailService.sendRevisionFailureEmail(username, moduleId, e.getMessage());
            throw new WorkflowException("Failed to create revision: " + e.getMessage(), e);
        }
    }

    private SubmoduleRevision reviseSubmodule(String submoduleId, String username) {
        Submodule submodule = submoduleRepository.findById(submoduleId)
            .orElseThrow(() -> new ObjectNotFoundException("Submodule not found: " + submoduleId));
        
        SubmoduleRevision latestRevision = submoduleRevisionRepository
            .findFirstBySubmoduleIdOrderByRevisionDesc(submoduleId)
            .orElseThrow(() -> new WorkflowException("No revision found"));
        
        if (latestRevision.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new WorkflowException("Cannot revise: latest revision is not released");
        }
        
        try {
            SubmoduleRevision newRevision = new SubmoduleRevision();
            newRevision.setSubmodule(submodule);
            copyRevisionData(latestRevision, newRevision);
            newRevision.setRevision(incrementRevision(latestRevision.getRevision()));
            newRevision.setReleaseStatus(ReleaseStatus.WORKING);
            
            SubmoduleRevision saved = submoduleRevisionRepository.save(newRevision);
            emailService.sendRevisionSuccessEmail(username, submoduleId, saved.getRevision());
            
            return saved;
        } catch (Exception e) {
            emailService.sendRevisionFailureEmail(username, submoduleId, e.getMessage());
            throw new WorkflowException("Failed to create revision: " + e.getMessage(), e);
        }
    }

    private PartRevision revisePart(String partId, String username) {
        Part part = partRepository.findById(partId)
            .orElseThrow(() -> new ObjectNotFoundException("Part not found: " + partId));
        
        PartRevision latestRevision = partRevisionRepository
            .findFirstByPartIdOrderByRevisionDesc(partId)
            .orElseThrow(() -> new WorkflowException("No revision found"));
        
        if (latestRevision.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new WorkflowException("Cannot revise: latest revision is not released");
        }
        
        try {
            PartRevision newRevision = new PartRevision();
            newRevision.setPart(part);
            copyRevisionData(latestRevision, newRevision);
            newRevision.setRevision(incrementRevision(latestRevision.getRevision()));
            newRevision.setReleaseStatus(ReleaseStatus.WORKING);
            
            PartRevision saved = partRevisionRepository.save(newRevision);
            emailService.sendRevisionSuccessEmail(username, partId, saved.getRevision());
            
            return saved;
        } catch (Exception e) {
            emailService.sendRevisionFailureEmail(username, partId, e.getMessage());
            throw new WorkflowException("Failed to create revision: " + e.getMessage(), e);
        }
    }

    private AuxiliaryRevision reviseAuxiliary(String auxiliaryId, String username) {
        Auxiliary auxiliary = auxiliaryRepository.findById(auxiliaryId)
            .orElseThrow(() -> new ObjectNotFoundException("Auxiliary not found: " + auxiliaryId));
        
        AuxiliaryRevision latestRevision = auxiliaryRevisionRepository
            .findFirstByAuxiliaryIdOrderByRevisionDesc(auxiliaryId)
            .orElseThrow(() -> new WorkflowException("No revision found"));
        
        if (latestRevision.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new WorkflowException("Cannot revise: latest revision is not released");
        }
        
        try {
            AuxiliaryRevision newRevision = new AuxiliaryRevision();
            newRevision.setAuxiliary(auxiliary);
            copyRevisionData(latestRevision, newRevision);
            newRevision.setRevision(incrementRevision(latestRevision.getRevision()));
            newRevision.setReleaseStatus(ReleaseStatus.WORKING);
            
            AuxiliaryRevision saved = auxiliaryRevisionRepository.save(newRevision);
            emailService.sendRevisionSuccessEmail(username, auxiliaryId, saved.getRevision());
            
            return saved;
        } catch (Exception e) {
            emailService.sendRevisionFailureEmail(username, auxiliaryId, e.getMessage());
            throw new WorkflowException("Failed to create revision: " + e.getMessage(), e);
        }
    }

    private void copyRevisionData(EBOMRevision source, EBOMRevision target) {
        target.setName(source.getName());
        target.setDepartment(source.getDepartment());
        target.setMass(source.getMass());
        target.setMaterial(source.getMaterial());
        target.setSupplier(source.getSupplier());
    }

    private String incrementRevision(String currentRevision) {
        try {
            int revNum = Integer.parseInt(currentRevision);
            return String.format("%02d", revNum + 1);
        } catch (NumberFormatException e) {
            throw new WorkflowException("Invalid revision format: " + currentRevision);
        }
    }
}