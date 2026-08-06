package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.exception.WorkflowException;
import com.peb.model.document.Document;
import com.peb.model.document.DocumentRevision;
import com.peb.model.document.DocumentTemplate;
import com.peb.model.document.DocumentTemplateRevision;
import com.peb.model.workflow.WorkflowInstance;
import com.peb.model.workflow.WorkflowStatus;
import com.peb.model.workflow.WorkflowType;
import com.peb.repository.DocumentRepository;
import com.peb.repository.DocumentRevisionRepository;
import com.peb.repository.DocumentTemplateRepository;
import com.peb.repository.DocumentTemplateRevisionRepository;
import com.peb.repository.WorkflowInstanceRepository;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Workflow-Service für Document Templates und Documents.
 * Verwaltet Release- und Revise-Operationen analog zu Design/eBOM.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentWorkflowService {

    private final DocumentTemplateRepository         templateRepository;
    private final DocumentTemplateRevisionRepository templateRevisionRepository;
    private final DocumentRepository                 documentRepository;
    private final DocumentRevisionRepository         documentRevisionRepository;
    private final WorkflowInstanceRepository         workflowInstanceRepository;
    private final EmailService                       emailService;

    // ═══════════════════════════════════════════════════════════
    // DOCUMENT TEMPLATE – RELEASE
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void releaseTemplateRevision(String templateId, Long revisionId, String username) {
        WorkflowInstance workflow = createWorkflow(WorkflowType.DOCUMENT_TEMPLATE_RELEASE,
                templateId, revisionId);
        try {
            DocumentTemplate template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new ObjectNotFoundException("Template nicht gefunden: " + templateId));

            DocumentTemplateRevision revision = templateRevisionRepository.findById(revisionId)
                    .orElseThrow(() -> new ObjectNotFoundException("Template-Revision nicht gefunden: " + revisionId));

            if (revision.getReleaseStatus() != ReleaseStatus.WORKING) {
                throw new WorkflowException("Nur WORKING-Revisionen können freigegeben werden.");
            }

            // Vorherige RELEASED-Revisions → OBSOLETE
            templateRevisionRepository
                    .findByTemplateIdAndReleaseStatusNot(templateId, ReleaseStatus.WORKING)
                    .stream()
                    .filter(r -> r.getReleaseStatus() == ReleaseStatus.RELEASED)
                    .forEach(r -> {
                        r.setReleaseStatus(ReleaseStatus.OBSOLETE);
                        templateRevisionRepository.save(r);
                    });

            revision.setReleaseStatus(ReleaseStatus.RELEASED);
            templateRevisionRepository.save(revision);

            workflow.setStatus(WorkflowStatus.SUCCESS);
            workflow.setCompletedDate(LocalDateTime.now());
            workflowInstanceRepository.save(workflow);

            log.info("Template-Revision freigegeben: templateId={}, revisionId={}", templateId, revisionId);
        } catch (Exception e) {
            workflow.setStatus(WorkflowStatus.FAILED);
            workflow.setErrorMessage(e.getMessage());
            workflowInstanceRepository.save(workflow);
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DOCUMENT TEMPLATE – REVISE
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public DocumentTemplateRevision reviseTemplate(String templateId, String username) {
        DocumentTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ObjectNotFoundException("Template nicht gefunden: " + templateId));

        DocumentTemplateRevision latest = template.getLatestRevision();
        if (latest == null || latest.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new WorkflowException("Revise nur bei RELEASED-Revision möglich.");
        }

        DocumentTemplateRevision newRevision = new DocumentTemplateRevision();
        newRevision.setTemplate(template);
        newRevision.setRevision(incrementRevision(latest.getRevision()));
        newRevision.setReleaseStatus(ReleaseStatus.WORKING);
        newRevision.setName(latest.getName());
        newRevision.setDepartment(latest.getDepartment());
        newRevision.setAttribute1(latest.getAttribute1());
        newRevision.setAttribute2(latest.getAttribute2());
        newRevision.setDocFilePath(latest.getDocFilePath());
        newRevision.setDocFileName(latest.getDocFileName());

        DocumentTemplateRevision saved = templateRevisionRepository.save(newRevision);
        log.info("Template revidiert: templateId={}, neue Revision={}", templateId, saved.getRevision());
        return saved;
    }

    // ═══════════════════════════════════════════════════════════
    // DOCUMENT – RELEASE
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public void releaseDocumentRevision(String documentId, Long revisionId, String username) {
        WorkflowInstance workflow = createWorkflow(WorkflowType.DOCUMENT_RELEASE, documentId, revisionId);
        try {
            documentRepository.findById(documentId)
                    .orElseThrow(() -> new ObjectNotFoundException("Document nicht gefunden: " + documentId));

            DocumentRevision revision = documentRevisionRepository.findById(revisionId)
                    .orElseThrow(() -> new ObjectNotFoundException("Dokument-Revision nicht gefunden: " + revisionId));

            if (revision.getReleaseStatus() != ReleaseStatus.WORKING) {
                throw new WorkflowException("Nur WORKING-Revisionen können freigegeben werden.");
            }
            if (revision.getDocFilePath() == null) {
                throw new WorkflowException(
                        "Das Dokument muss zuerst hochgeladen werden, bevor es freigegeben werden kann.");
            }

            // Vorherige RELEASED → OBSOLETE
            documentRevisionRepository
                    .findByDocumentIdAndReleaseStatusNot(documentId, ReleaseStatus.WORKING)
                    .stream()
                    .filter(r -> r.getReleaseStatus() == ReleaseStatus.RELEASED)
                    .forEach(r -> {
                        r.setReleaseStatus(ReleaseStatus.OBSOLETE);
                        documentRevisionRepository.save(r);
                    });

            revision.setReleaseStatus(ReleaseStatus.RELEASED);
            documentRevisionRepository.save(revision);

            workflow.setStatus(WorkflowStatus.SUCCESS);
            workflow.setCompletedDate(LocalDateTime.now());
            workflowInstanceRepository.save(workflow);

            log.info("Dokument-Revision freigegeben: documentId={}, revisionId={}", documentId, revisionId);
        } catch (Exception e) {
            workflow.setStatus(WorkflowStatus.FAILED);
            workflow.setErrorMessage(e.getMessage());
            workflowInstanceRepository.save(workflow);
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DOCUMENT – REVISE
    // ═══════════════════════════════════════════════════════════

    @Transactional
    public DocumentRevision reviseDocument(String documentId, String username) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ObjectNotFoundException("Document nicht gefunden: " + documentId));

        DocumentRevision latest = document.getLatestRevision();
        if (latest == null || latest.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new WorkflowException("Revise nur bei RELEASED-Revision möglich.");
        }

        DocumentRevision newRevision = new DocumentRevision();
        newRevision.setDocument(document);
        newRevision.setRevision(incrementRevision(latest.getRevision()));
        newRevision.setReleaseStatus(ReleaseStatus.WORKING);
        newRevision.setName(latest.getName());
        newRevision.setDepartment(latest.getDepartment());
        newRevision.setAttribute1(latest.getAttribute1());
        newRevision.setAttribute2(latest.getAttribute2());
        newRevision.setTemplateRevision(latest.getTemplateRevision());
        // Neue Revision startet ohne Datei – Nutzer muss neue .doc hochladen

        DocumentRevision saved = documentRevisionRepository.save(newRevision);
        log.info("Dokument revidiert: documentId={}, neue Revision={}", documentId, saved.getRevision());
        return saved;
    }

    // ─────────────────────────────────────────────
    // HILFSMETHODEN
    // ─────────────────────────────────────────────

    private WorkflowInstance createWorkflow(WorkflowType type, String objectId, Long revisionId) {
        WorkflowInstance wf = new WorkflowInstance();
        wf.setWorkflowType(type);
        wf.setObjectId(objectId);
        wf.setRevisionId(revisionId);
        wf.setStatus(WorkflowStatus.IN_PROGRESS);
        return workflowInstanceRepository.save(wf);
    }

    private String incrementRevision(String current) {
        try {
            int n = Integer.parseInt(current);
            return String.format("%02d", n + 1);
        } catch (NumberFormatException e) {
            throw new WorkflowException("Ungültiges Revisionsformat: " + current);
        }
    }
}
