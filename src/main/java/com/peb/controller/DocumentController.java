package com.peb.controller;

import com.peb.dto.DocumentDTO;
import com.peb.model.document.Document;
import com.peb.model.document.DocumentRevision;
import com.peb.model.document.DocumentTemplate;
import com.peb.service.DocumentService;
import com.peb.service.DocumentTemplateService;
import com.peb.service.DocumentWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService         documentService;
    private final DocumentTemplateService templateService;
    private final DocumentWorkflowService workflowService;

    // ─────────────────────────────────────────────
    // LIST
    // ─────────────────────────────────────────────

    @GetMapping
    public String listDocuments(Model model) {
        model.addAttribute("documents", documentService.getAllDocuments());
        return "documents/document-list";
    }

    // ─────────────────────────────────────────────
    // CREATE (aus Template)
    // ─────────────────────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String newDocumentForm(Model model) {
        List<DocumentTemplate> releasedTemplates = templateService.getReleasedTemplates();
        model.addAttribute("document", new DocumentDTO());
        model.addAttribute("releasedTemplates", releasedTemplates);
        return "documents/document-form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE')")
    public String createDocument(@ModelAttribute DocumentDTO dto, Model model) {
        try {
            Document document = documentService.createFromTemplate(
                    dto.getName(),
                    dto.getDepartment(),
                    dto.getDescription(),
                    dto.getAttribute1(),
                    dto.getAttribute2(),
                    dto.getTemplateId());
            return "redirect:/documents/" + document.getId() + "?created=true";
        } catch (Exception e) {
            model.addAttribute("error", "Fehler beim Erstellen: " + e.getMessage());
            model.addAttribute("document", dto);
            model.addAttribute("releasedTemplates", templateService.getReleasedTemplates());
            return "documents/document-form";
        }
    }

    // ─────────────────────────────────────────────
    // EDIT
    // ─────────────────────────────────────────────

	@GetMapping("/{id}/edit")
	@PreAuthorize("hasAuthority('WRITE')")
	public String editDocumentForm(@PathVariable String id, Model model) {
		Document document = documentService.getDocument(id);
		DocumentDTO dto = new DocumentDTO();
		dto.setName(document.getName());
		dto.setDepartment(document.getDepartment());
		dto.setDescription(document.getDescription());
		dto.setAttribute1(document.getAttribute1());
		dto.setAttribute2(document.getAttribute2());
		dto.setTemplateId(document.getTemplate() != null ? document.getTemplate().getId() : "");
		model.addAttribute("document", dto);
		model.addAttribute("documentId", id);
		model.addAttribute("templateName", document.getTemplate() != null ? document.getTemplate().getName() : "");

		// ── NEU: Attribute sperren, sobald eine Datei existiert ──
		DocumentRevision latest = document.getLatestRevision();
		boolean attributesLocked = latest != null && latest.getDocFilePath() != null;
		model.addAttribute("attributesLocked", attributesLocked);

		return "documents/document-edit";
	}

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String updateDocument(@PathVariable String id,
                                  @ModelAttribute DocumentDTO dto,
                                  Model model) {
        try {
            documentService.updateDocument(id,
                    dto.getName(), dto.getDepartment(), dto.getDescription(),
                    dto.getAttribute1(), dto.getAttribute2());
            return "redirect:/documents/" + id + "?updated=true";
        } catch (Exception e) {
            model.addAttribute("error", "Fehler beim Speichern: " + e.getMessage());
            model.addAttribute("document", dto);
            model.addAttribute("documentId", id);
            return "documents/document-edit";
        }
    }
	
	@PostMapping("/{id}/revisions/{revisionId}/delete-file")
	@PreAuthorize("hasAuthority('WRITE')")
	public String deleteDocumentFile(@PathVariable String id,
									  @PathVariable Long revisionId,
									  Model model) {
		try {
			documentService.deleteDocumentFile(revisionId);
			return "redirect:/documents/" + id + "?fileDeleted=true";
		} catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			return viewDocument(id, model);
		}
	}

    // ─────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String deleteDocument(@PathVariable String id, Model model) {
        try {
            documentService.deleteDocument(id);
            return "redirect:/documents?deleted=true";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return viewDocument(id, model);
        }
    }

    // ─────────────────────────────────────────────
    // VIEW
    // ─────────────────────────────────────────────

    @GetMapping("/{id}")
    public String viewDocument(@PathVariable String id, Model model) {
        Document document = documentService.getDocument(id);
        List<DocumentRevision> revisions = documentService.getRevisions(id);
        model.addAttribute("document", document);
        model.addAttribute("revisions", revisions);
        return "documents/document-view";
    }

    // ─────────────────────────────────────────────
    // DOWNLOAD – vorausgefülltes Template (zum lokalen Bearbeiten)
    // ─────────────────────────────────────────────

    @GetMapping("/{id}/revisions/{revisionId}/download-prefilled")
    public ResponseEntity<ByteArrayResource> downloadPrefilled(
            @PathVariable String id,
            @PathVariable Long revisionId) throws IOException {

        byte[] docBytes = documentService.generatePrefilledDocument(revisionId);
        DocumentRevision revision = documentService.getRevision(revisionId);
        String fileName = "document_" + id + "_rev" + revision.getRevision() + ".docx";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(new ByteArrayResource(docBytes));
    }
	

    // ─────────────────────────────────────────────
    // UPLOAD – finale .doc-Datei (nach lokalem Bearbeiten)
    // ─────────────────────────────────────────────

    @PostMapping("/{id}/revisions/{revisionId}/upload")
    @PreAuthorize("hasAuthority('WRITE')")
    public String uploadFinalDocument(@PathVariable String id,
                                       @PathVariable Long revisionId,
                                       @RequestParam("docFile") MultipartFile docFile,
                                       Model model) {
        try {
            documentService.uploadFinalDocument(revisionId, docFile);
            return "redirect:/documents/" + id + "?uploaded=true";
        } catch (IOException | IllegalArgumentException e) {
            model.addAttribute("error", "Fehler beim Upload: " + e.getMessage());
            return viewDocument(id, model);
        }
    }

    // ─────────────────────────────────────────────
    // DOWNLOAD – finale .doc-Datei
    // ─────────────────────────────────────────────

    @GetMapping("/{id}/revisions/{revisionId}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String id,
                                                      @PathVariable Long revisionId) {
        Resource resource = documentService.loadDocFile(revisionId);
        DocumentRevision revision = documentService.getRevision(revisionId);
        String fileName = revision.getDocFileName() != null
                ? revision.getDocFileName() : "document.doc";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    // ─────────────────────────────────────────────
    // RELEASE
    // ─────────────────────────────────────────────

    @PostMapping("/{id}/revisions/{revisionId}/release")
    @PreAuthorize("hasAuthority('WRITE')")
    public String releaseRevision(@PathVariable String id,
                                   @PathVariable Long revisionId,
                                   Authentication auth,
                                   Model model) {
        try {
            workflowService.releaseDocumentRevision(id, revisionId, auth.getName());
            return "redirect:/documents/" + id + "?released=true";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return viewDocument(id, model);
        }
    }
	

	@PostMapping("/{id}/revisions/{revisionId}/generate-prefilled")
	@PreAuthorize("hasAuthority('WRITE')")
	public String generateAndSavePrefilled(@PathVariable String id,
										   @PathVariable Long revisionId,
										   Model model) {
		try {
			documentService.generateAndSavePrefilledDocument(revisionId);
			// "updated=true" löst die grüne Erfolgsmeldung in der HTML aus
			return "redirect:/documents/" + id + "?updated=true"; 
		} catch (Exception e) {
/* 			log.error("Fehler bei der automatischen Generierung des Dokuments.", e); */
			model.addAttribute("error", "Fehler bei der Generierung: " + e.getMessage());
			// Bei Fehler: Ansicht mit Fehlermeldung neu laden
			return viewDocument(id, model); 
		}
	}


    // ─────────────────────────────────────────────
    // REVISE
    // ─────────────────────────────────────────────

    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAuthority('WRITE')")
    public String reviseDocument(@PathVariable String id,
                                  Authentication auth,
                                  Model model) {
        try {
            workflowService.reviseDocument(id, auth.getName());
            return "redirect:/documents/" + id + "?revised=true";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return viewDocument(id, model);
        }
    }
}
