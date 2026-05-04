package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.exception.WorkflowException;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.model.workflow.WorkflowInstance;
import com.peb.model.workflow.WorkflowStatus;
import com.peb.model.workflow.WorkflowType;
import com.peb.repository.DesignRepository;
import com.peb.repository.DesignRevisionRepository;
import com.peb.repository.WorkflowInstanceRepository;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing workflow operations
 * Handles release workflows for Design and eBOM objects
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final DesignRepository designRepository;
    private final DesignRevisionRepository designRevisionRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final EmailService emailService;

    @Async
    @Transactional
    public void releaseDesignRevision(String designId, Long revisionId, String username) {
        WorkflowInstance workflow = createWorkflowInstance(
            WorkflowType.DESIGN_RELEASE, designId, revisionId
        );

        try {
            Design design = designRepository.findById(designId)
                .orElseThrow(() -> new ObjectNotFoundException("Design not found: " + designId));

            DesignRevision revision = designRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new ObjectNotFoundException("Revision not found: " + revisionId));

            if (revision.getReleaseStatus() != ReleaseStatus.WORKING) {
                throw new WorkflowException("Only working revisions can be released");
            }

            // Set current revision to RELEASED
            revision.setReleaseStatus(ReleaseStatus.RELEASED);
            designRevisionRepository.save(revision);

            // Set all older revisions to OBSOLETE
            List<DesignRevision> olderRevisions = designRevisionRepository
                .findByDesignIdAndReleaseStatusNot(designId, ReleaseStatus.WORKING);
            
            for (DesignRevision older : olderRevisions) {
                if (!older.getRevisionId().equals(revisionId)) {
                    older.setReleaseStatus(ReleaseStatus.OBSOLETE);
                    designRevisionRepository.save(older);
                }
            }

            completeWorkflow(workflow, WorkflowStatus.SUCCESS, null);
            emailService.sendReleaseSuccessEmail(username, designId, revision.getRevision());

        } catch (Exception e) {
            completeWorkflow(workflow, WorkflowStatus.FAILED, e.getMessage());
            emailService.sendReleaseFailureEmail(username, designId, e.getMessage());
            throw new WorkflowException("Release workflow failed", e);
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