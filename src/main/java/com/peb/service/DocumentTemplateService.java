package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.exception.WorkflowException;
import com.peb.model.document.DocumentTemplate;
import com.peb.model.document.DocumentTemplateRevision;
import com.peb.repository.DocumentTemplateRepository;
import com.peb.repository.DocumentTemplateRevisionRepository;
import com.peb.service.search.SearchIndexService;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Service für Document Template Verwaltung
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentTemplateService {

    private final DocumentTemplateRepository          templateRepository;
    private final DocumentTemplateRevisionRepository  revisionRepository;
    private final IDGeneratorService                  idGeneratorService;
    private final SearchIndexService                  searchIndexService; // NEU: zentrale Suche

    @Value("${peb.document.storage-dir:data/documents/}")
    private String storageDir;

    // ─────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────

    @Transactional
    public DocumentTemplate createTemplate(String name, String department,
                                           String description,
                                           String attribute1, String attribute2,
                                           MultipartFile docFile) throws IOException {
        validateDocFile(docFile);

        DocumentTemplate template = new DocumentTemplate();
        template.setId(idGeneratorService.generateId(ObjectType.DOCUMENT_TEMPLATE));
        template.setName(name);
        template.setDepartment(department);
        template.setDescription(description);
        template.setAttribute1(attribute1);
        template.setAttribute2(attribute2);
        template = templateRepository.save(template);

        // Datei speichern
        String filePath = storeFile(docFile);

        // Initiale Revision anlegen
        DocumentTemplateRevision revision = new DocumentTemplateRevision();
        revision.setTemplate(template);
        revision.setRevision("01");
        revision.setReleaseStatus(ReleaseStatus.WORKING);
        revision.setDocFilePath(filePath);
        revision.setDocFileName(docFile.getOriginalFilename());
        revision.syncFromParent();
        revisionRepository.save(revision);
        template.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort greift

        log.info("DocumentTemplate erstellt: id={}", template.getId());

        // NEU: zentrale Suche aktualisieren
        searchIndexService.index(ObjectType.DOCUMENT_TEMPLATE, template);

        return template;
    }

    @Transactional(readOnly = true)
    public DocumentTemplate getTemplate(String id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("DocumentTemplate nicht gefunden: " + id));
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplateRevision> getRevisions(String templateId) {
        return revisionRepository.findByTemplateId(templateId);
    }

    @Transactional(readOnly = true)
    public DocumentTemplateRevision getRevision(Long revisionId) {
        return revisionRepository.findById(revisionId)
                .orElseThrow(() -> new ObjectNotFoundException("Template-Revision nicht gefunden: " + revisionId));
    }

    /** Gibt alle freigegebenen Templates zurück (für Dokument-Erstellung) */
    @Transactional(readOnly = true)
    public List<DocumentTemplate> getReleasedTemplates() {
        return templateRepository.findAll().stream()
                .filter(t -> t.getReleasedRevision() != null)
                .toList();
    }

    // ─────────────────────────────────────────────
    // UPDATE / DELETE
    // ─────────────────────────────────────────────

    @Transactional
    public DocumentTemplate updateTemplate(String id, String name, String department,
                                           String description,
                                           String attribute1, String attribute2) {
        DocumentTemplate template = getTemplate(id);
        template.setName(name);
        template.setDepartment(department);
        template.setDescription(description);
        template.setAttribute1(attribute1);
        template.setAttribute2(attribute2);

        // Aktuelle WORKING-Revision synchronisieren
        DocumentTemplateRevision latest = template.getLatestRevision();
        if (latest != null && latest.getReleaseStatus() == ReleaseStatus.WORKING) {
            latest.syncFromParent();
            revisionRepository.save(latest);
        }

        DocumentTemplate saved = templateRepository.save(template);

        // NEU: zentrale Suche aktualisieren
        searchIndexService.index(ObjectType.DOCUMENT_TEMPLATE, saved);

        return saved;
    }

    @Transactional
    public void deleteTemplate(String id) {
        DocumentTemplate template = getTemplate(id);

        // Sicherheitsprüfung: keine freigegebene Revision vorhanden?
        boolean hasReleased = template.getRevisions().stream()
                .anyMatch(r -> r.getReleaseStatus() == ReleaseStatus.RELEASED);
        if (hasReleased) {
            throw new WorkflowException(
                    "Template '" + id + "' kann nicht gelöscht werden, "
                    + "da eine freigegebene Revision existiert.");
        }

        // Dateien vom Dateisystem löschen
        template.getRevisions().forEach(rev -> deleteFile(rev.getDocFilePath()));

        templateRepository.delete(template);
        log.info("DocumentTemplate gelöscht: id={}", id);

        // NEU: aus zentraler Suche entfernen
        searchIndexService.remove(ObjectType.DOCUMENT_TEMPLATE, id);
    }

    @Transactional
    public DocumentTemplateRevision updateRevision(Long revisionId, String attribute1,
                                                    String attribute2, MultipartFile docFile) throws IOException {
        DocumentTemplateRevision revision = getRevision(revisionId);
        if (revision.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new WorkflowException("Nur Revisionen mit Status WORKING können bearbeitet werden.");
        }

        revision.setAttribute1(attribute1);
        revision.setAttribute2(attribute2);

        if (docFile != null && !docFile.isEmpty()) {
            validateDocFile(docFile);
            String filePath = storeFile(docFile);
            revision.setDocFilePath(filePath);
            revision.setDocFileName(docFile.getOriginalFilename());
        }

        return revisionRepository.save(revision);
    }

    // ─────────────────────────────────────────────
    // DATEI-OPERATIONEN
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Resource loadDocFile(Long revisionId) {
        DocumentTemplateRevision revision = getRevision(revisionId);
        if (revision.getDocFilePath() == null) {
            throw new ObjectNotFoundException("Keine Datei für Template-Revision: " + revisionId);
        }
        try {
            Path path = Paths.get(revision.getDocFilePath());
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ObjectNotFoundException("Template-Datei nicht lesbar: " + revision.getDocFilePath());
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ObjectNotFoundException("Ungültiger Dateipfad: " + revision.getDocFilePath());
        }
    }

    // ─────────────────────────────────────────────
    // HILFSMETHODEN
    // ─────────────────────────────────────────────

    private String storeFile(MultipartFile file) throws IOException {
        String uniqueName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(storageDir, uniqueName);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, file.getBytes());
        return filePath.toString();
    }

    private void deleteFile(String filePath) {
        if (filePath == null) return;
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.warn("Datei konnte nicht gelöscht werden: {}", filePath);
        }
    }

    private void validateDocFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new WorkflowException("Keine Datei hochgeladen.");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".docx")) {
            throw new WorkflowException("Nur .docx-Dateien sind erlaubt.");
        }
    }
}
