package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.exception.WorkflowException;
import com.peb.model.ebom.*;
import com.peb.model.workflow.WorkflowInstance;
import com.peb.model.workflow.WorkflowStatus;
import com.peb.model.workflow.WorkflowType;
import com.peb.repository.*;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for eBOM workflow operations (Release)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EBOMWorkflowService {

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
    
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final EmailService emailService;

    @Async
    @Transactional
    public void releaseEBOMRevision(String objectId, String username) {
        String prefix = objectId.substring(0, 2);
        
        try {
            switch (prefix) {
                case "MA" -> releaseMachine(objectId, username);
                case "SY" -> releaseSystem(objectId, username);
                case "SU" -> releaseSubsystem(objectId, username);
                case "MO" -> releaseModule(objectId, username);
                case "MU" -> releaseSubmodule(objectId, username);
                case "PT" -> releasePart(objectId, username);
                case "AX" -> releaseAuxiliary(objectId, username);
                default -> throw new ObjectNotFoundException("Unknown object type: " + prefix);
            }
        } catch (Exception e) {
            log.error("Failed to release eBOM object {}: {}", objectId, e.getMessage(), e);
            emailService.sendReleaseFailureEmail(username, objectId, e.getMessage());
            throw new WorkflowException("Release failed: " + e.getMessage(), e);
        }
    }

    private void releaseMachine(String machineId, String username) {
        Machine machine = machineRepository.findById(machineId)
            .orElseThrow(() -> new ObjectNotFoundException("Machine not found: " + machineId));

        MachineRevision currentRevision = machineRevisionRepository
            .findFirstByMachineIdOrderByRevisionDesc(machineId)
            .orElseThrow(() -> new WorkflowException("No revision found"));

        if (currentRevision.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new WorkflowException("Only working revisions can be released");
        }

        WorkflowInstance workflow = createWorkflowInstance(
            WorkflowType.EBOM_RELEASE, machineId, currentRevision.getRevisionId()
        );

        try {
            // Set current revision to RELEASED
            currentRevision.setReleaseStatus(ReleaseStatus.RELEASED);
            machineRevisionRepository.save(currentRevision);

            // Set all older revisions to OBSOLETE
            List<MachineRevision> olderRevisions = machineRevisionRepository
                .findByMachineIdAndReleaseStatusNot(machineId, ReleaseStatus.WORKING);

            for (MachineRevision older : olderRevisions) {
                if (!older.getRevisionId().equals(currentRevision.getRevisionId())) {
                    older.setReleaseStatus(ReleaseStatus.OBSOLETE);
                    machineRevisionRepository.save(older);
                }
            }

            completeWorkflow(workflow, WorkflowStatus.SUCCESS, null);
            emailService.sendReleaseSuccessEmail(username, machineId, currentRevision.getRevision());
            log.info("Released Machine {} revision {}", machineId, currentRevision.getRevision());

        } catch (Exception e) {
            completeWorkflow(workflow, WorkflowStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private void releaseSystem(String systemId, String username) {
        com.peb.model.ebom.System system = systemRepository.findById(systemId)
            .orElseThrow(() -> new ObjectNotFoundException("System not found: " + systemId));

        SystemRevision currentRevision = systemRevisionRepository
            .findFirstBySystemIdOrderByRevisionDesc(systemId)
            .orElseThrow(() -> new WorkflowException("No revision found"));

        if (currentRevision.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new WorkflowException("Only working revisions can be released");
        }

        WorkflowInstance workflow = createWorkflowInstance(
            WorkflowType.EBOM_RELEASE, systemId, currentRevision.getRevisionId()
        );

        try {
            currentRevision.setReleaseStatus(ReleaseStatus.RELEASED);
            systemRevisionRepository.save(currentRevision);

            List<SystemRevision> olderRevisions = systemRevisionRepository
                .findBySystemIdAndReleaseStatusNot(systemId, ReleaseStatus.WORKING);

            for (SystemRevision older : olderRevisions) {
                if (!older.getRevisionId().equals(currentRevision.getRevisionId())) {
                    older.setReleaseStatus(ReleaseStatus.OBSOLETE);
                    systemRevisionRepository.save(older);
                }
            }

            completeWorkflow(workflow, WorkflowStatus.SUCCESS, null);
            emailService.sendReleaseSuccessEmail(username, systemId, currentRevision.getRevision());
            log.info("Released System {} revision {}", systemId, currentRevision.getRevision());

        } catch (Exception e) {
            completeWorkflow(workflow, WorkflowStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private void releaseSubsystem(String subsystemId, String username) {
        Subsystem subsystem = subsystemRepository.findById(subsystemId)
            .orElseThrow(() -> new ObjectNotFoundException("Subsystem not found: " + subsystemId));

        SubsystemRevision currentRevision = subsystemRevisionRepository
            .findFirstBySubsystemIdOrderByRevisionDesc(subsystemId)
            .orElseThrow(() -> new WorkflowException("No revision found"));

        if (currentRevision.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new WorkflowException("Only working revisions can be released");
        }

        WorkflowInstance workflow = createWorkflowInstance(
            WorkflowType.EBOM_RELEASE, subsystemId, currentRevision.getRevisionId()
        );

        try {
            currentRevision.setReleaseStatus(ReleaseStatus.RELEASED);
            subsystemRevisionRepository.save(currentRevision);

            List<SubsystemRevision> olderRevisions = subsystemRevisionRepository
                .findBySubsystemIdAndReleaseStatusNot(subsystemId, ReleaseStatus.WORKING);

            for (SubsystemRevision older : olderRevisions) {
                if (!older.getRevisionId().equals(currentRevision.getRevisionId())) {
                    older.setReleaseStatus(ReleaseStatus.OBSOLETE);
                    subsystemRevisionRepository.save(older);
                }
            }

            completeWorkflow(workflow, WorkflowStatus.SUCCESS, null);
            emailService.sendReleaseSuccessEmail(username, subsystemId, currentRevision.getRevision());
            log.info("Released Subsystem {} revision {}", subsystemId, currentRevision.getRevision());

        } catch (Exception e) {
            completeWorkflow(workflow, WorkflowStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private void releaseModule(String moduleId, String username) {
        EBOMModule module = ebomModuleRepository.findById(moduleId)
            .orElseThrow(() -> new ObjectNotFoundException("Module not found: " + moduleId));

        EBOMModuleRevision currentRevision = ebomModuleRevisionRepository
            .findFirstByModuleIdOrderByRevisionDesc(moduleId)
            .orElseThrow(() -> new WorkflowException("No revision found"));

        if (currentRevision.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new WorkflowException("Only working revisions can be released");
        }

        WorkflowInstance workflow = createWorkflowInstance(
            WorkflowType.EBOM_RELEASE, moduleId, currentRevision.getRevisionId()
        );

        try {
            currentRevision.setReleaseStatus(ReleaseStatus.RELEASED);
            ebomModuleRevisionRepository.save(currentRevision);

            List<EBOMModuleRevision> olderRevisions = ebomModuleRevisionRepository
                .findByModuleIdAndReleaseStatusNot(moduleId, ReleaseStatus.WORKING);

            for (EBOMModuleRevision older : olderRevisions) {
                if (!older.getRevisionId().equals(currentRevision.getRevisionId())) {
                    older.setReleaseStatus(ReleaseStatus.OBSOLETE);
                    ebomModuleRevisionRepository.save(older);
                }
            }

            completeWorkflow(workflow, WorkflowStatus.SUCCESS, null);
            emailService.sendReleaseSuccessEmail(username, moduleId, currentRevision.getRevision());
            log.info("Released Module {} revision {}", moduleId, currentRevision.getRevision());

        } catch (Exception e) {
            completeWorkflow(workflow, WorkflowStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private void releaseSubmodule(String submoduleId, String username) {
        Submodule submodule = submoduleRepository.findById(submoduleId)
            .orElseThrow(() -> new ObjectNotFoundException("Submodule not found: " + submoduleId));

        SubmoduleRevision currentRevision = submoduleRevisionRepository
            .findFirstBySubmoduleIdOrderByRevisionDesc(submoduleId)
            .orElseThrow(() -> new WorkflowException("No revision found"));

        if (currentRevision.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new WorkflowException("Only working revisions can be released");
        }

        WorkflowInstance workflow = createWorkflowInstance(
            WorkflowType.EBOM_RELEASE, submoduleId, currentRevision.getRevisionId()
        );

        try {
            currentRevision.setReleaseStatus(ReleaseStatus.RELEASED);
            submoduleRevisionRepository.save(currentRevision);

            List<SubmoduleRevision> olderRevisions = submoduleRevisionRepository
                .findBySubmoduleIdAndReleaseStatusNot(submoduleId, ReleaseStatus.WORKING);

            for (SubmoduleRevision older : olderRevisions) {
                if (!older.getRevisionId().equals(currentRevision.getRevisionId())) {
                    older.setReleaseStatus(ReleaseStatus.OBSOLETE);
                    submoduleRevisionRepository.save(older);
                }
            }

            completeWorkflow(workflow, WorkflowStatus.SUCCESS, null);
            emailService.sendReleaseSuccessEmail(username, submoduleId, currentRevision.getRevision());
            log.info("Released Submodule {} revision {}", submoduleId, currentRevision.getRevision());

        } catch (Exception e) {
            completeWorkflow(workflow, WorkflowStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private void releasePart(String partId, String username) {
        Part part = partRepository.findById(partId)
            .orElseThrow(() -> new ObjectNotFoundException("Part not found: " + partId));

        PartRevision currentRevision = partRevisionRepository
            .findFirstByPartIdOrderByRevisionDesc(partId)
            .orElseThrow(() -> new WorkflowException("No revision found"));

        if (currentRevision.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new WorkflowException("Only working revisions can be released");
        }

        WorkflowInstance workflow = createWorkflowInstance(
            WorkflowType.EBOM_RELEASE, partId, currentRevision.getRevisionId()
        );

        try {
            currentRevision.setReleaseStatus(ReleaseStatus.RELEASED);
            partRevisionRepository.save(currentRevision);

            List<PartRevision> olderRevisions = partRevisionRepository
                .findByPartIdAndReleaseStatusNot(partId, ReleaseStatus.WORKING);

            for (PartRevision older : olderRevisions) {
                if (!older.getRevisionId().equals(currentRevision.getRevisionId())) {
                    older.setReleaseStatus(ReleaseStatus.OBSOLETE);
                    partRevisionRepository.save(older);
                }
            }

            completeWorkflow(workflow, WorkflowStatus.SUCCESS, null);
            emailService.sendReleaseSuccessEmail(username, partId, currentRevision.getRevision());
            log.info("Released Part {} revision {}", partId, currentRevision.getRevision());

        } catch (Exception e) {
            completeWorkflow(workflow, WorkflowStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private void releaseAuxiliary(String auxiliaryId, String username) {
        Auxiliary auxiliary = auxiliaryRepository.findById(auxiliaryId)
            .orElseThrow(() -> new ObjectNotFoundException("Auxiliary not found: " + auxiliaryId));

        AuxiliaryRevision currentRevision = auxiliaryRevisionRepository
            .findFirstByAuxiliaryIdOrderByRevisionDesc(auxiliaryId)
            .orElseThrow(() -> new WorkflowException("No revision found"));

        if (currentRevision.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new WorkflowException("Only working revisions can be released");
        }

        WorkflowInstance workflow = createWorkflowInstance(
            WorkflowType.EBOM_RELEASE, auxiliaryId, currentRevision.getRevisionId()
        );

        try {
            currentRevision.setReleaseStatus(ReleaseStatus.RELEASED);
            auxiliaryRevisionRepository.save(currentRevision);

            List<AuxiliaryRevision> olderRevisions = auxiliaryRevisionRepository
                .findByAuxiliaryIdAndReleaseStatusNot(auxiliaryId, ReleaseStatus.WORKING);

            for (AuxiliaryRevision older : olderRevisions) {
                if (!older.getRevisionId().equals(currentRevision.getRevisionId())) {
                    older.setReleaseStatus(ReleaseStatus.OBSOLETE);
                    auxiliaryRevisionRepository.save(older);
                }
            }

            completeWorkflow(workflow, WorkflowStatus.SUCCESS, null);
            emailService.sendReleaseSuccessEmail(username, auxiliaryId, currentRevision.getRevision());
            log.info("Released Auxiliary {} revision {}", auxiliaryId, currentRevision.getRevision());

        } catch (Exception e) {
            completeWorkflow(workflow, WorkflowStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    private WorkflowInstance createWorkflowInstance(WorkflowType type, String objectId, Long revisionId) {
        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setWorkflowType(type);
        workflow.setObjectId(objectId);
        workflow.setRevisionId(revisionId);
        workflow.setStatus(WorkflowStatus.IN_PROGRESS);
        return workflowInstanceRepository.save(workflow);
    }

    private void completeWorkflow(WorkflowInstance workflow, WorkflowStatus status, String errorMessage) {
        workflow.setStatus(status);
        workflow.setErrorMessage(errorMessage);
        workflow.setCompletedDate(LocalDateTime.now());
        workflowInstanceRepository.save(workflow);
    }
}