package com.peb.controller;

import com.peb.dto.DocumentTemplateDTO;
import com.peb.model.document.DocumentTemplate;
import com.peb.model.document.DocumentTemplateRevision;
import com.peb.service.DocumentTemplateService;
import com.peb.service.DocumentWorkflowService;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/document-templates")
@RequiredArgsConstructor
public class DocumentTemplateController {

    private final DocumentTemplateService  templateService;
    private final DocumentWorkflowService  workflowService;

    // ─────────────────────────────────────────────
    // LIST
    // ─────────────────────────────────────────────

    @GetMapping
    public String listTemplates(Model model) {
        List<DocumentTemplate> templates = templateService.getAllTemplates();
        model.addAttribute("templates", templates);
        return "document-templates/doc-template-list";
    }

    // ─────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String newTemplateForm(Model model) {
        model.addAttribute("template", new DocumentTemplateDTO());
        return "document-templates/doc-template-form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE')")
    public String createTemplate(@ModelAttribute DocumentTemplateDTO dto,
                                  @RequestParam("docFile") MultipartFile docFile,
                                  Model model) {
        try {
            DocumentTemplate template = templateService.createTemplate(
                    dto.getName(),
                    dto.getDepartment(),
                    dto.getDescription(),
                    dto.getAttribute1(),
                    dto.getAttribute2(),
                    docFile);
            return "redirect:/document-templates/" + template.getId();
        } catch (IOException | IllegalArgumentException e) {
            model.addAttribute("error", "Fehler beim Erstellen: " + e.getMessage());
            model.addAttribute("template", dto);
            return "document-templates/doc-template-form";
        }
    }

    // ─────────────────────────────────────────────
    // VIEW
    // ─────────────────────────────────────────────

    @GetMapping("/{id}")
    public String viewTemplate(@PathVariable String id, Model model) {
        DocumentTemplate template = templateService.getTemplate(id);
        List<DocumentTemplateRevision> revisions = templateService.getRevisions(id);
        model.addAttribute("template", template);
        model.addAttribute("revisions", revisions);
        return "document-templates/doc-template-view";
    }

    // ─────────────────────────────────────────────
    // EDIT
    // ─────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editTemplateForm(@PathVariable String id, Model model) {
        DocumentTemplate template = templateService.getTemplate(id);
        DocumentTemplateDTO dto = new DocumentTemplateDTO();
        dto.setName(template.getName());
        dto.setDepartment(template.getDepartment());
        dto.setDescription(template.getDescription());
        dto.setAttribute1(template.getAttribute1());
        dto.setAttribute2(template.getAttribute2());
        model.addAttribute("template", dto);
        model.addAttribute("templateId", id);
        return "document-templates/doc-template-edit";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String updateTemplate(@PathVariable String id,
                                  @ModelAttribute DocumentTemplateDTO dto,
                                  Model model) {
        try {
            templateService.updateTemplate(id,
                    dto.getName(), dto.getDepartment(), dto.getDescription(),
                    dto.getAttribute1(), dto.getAttribute2());
            return "redirect:/document-templates/" + id + "?updated=true";
        } catch (Exception e) {
            model.addAttribute("error", "Fehler beim Speichern: " + e.getMessage());
            model.addAttribute("template", dto);
            model.addAttribute("templateId", id);
            return "document-templates/doc-template-edit";
        }
    }

    // ─────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String deleteTemplate(@PathVariable String id, Model model) {
        try {
            templateService.deleteTemplate(id);
            return "redirect:/document-templates?deleted=true";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return viewTemplate(id, model);
        }
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
            workflowService.releaseTemplateRevision(id, revisionId, auth.getName());
            return "redirect:/document-templates/" + id + "?released=true";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return viewTemplate(id, model);
        }
    }

    // ─────────────────────────────────────────────
    // REVISE
    // ─────────────────────────────────────────────

    @PostMapping("/{id}/revise")
    @PreAuthorize("hasAuthority('WRITE')")
    public String reviseTemplate(@PathVariable String id,
                                  Authentication auth,
                                  Model model) {
        try {
            workflowService.reviseTemplate(id, auth.getName());
            return "redirect:/document-templates/" + id + "?revised=true";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return viewTemplate(id, model);
        }
    }

    // ─────────────────────────────────────────────
    // UPDATE REVISION (neue .doc-Datei hochladen)
    // ─────────────────────────────────────────────

    @PostMapping("/{id}/revisions/{revisionId}/update")
    @PreAuthorize("hasAuthority('WRITE')")
    public String updateRevision(@PathVariable String id,
                                  @PathVariable Long revisionId,
                                  @RequestParam(value = "attribute1", required = false) String attribute1,
                                  @RequestParam(value = "attribute2", required = false) String attribute2,
                                  @RequestParam(value = "docFile", required = false) MultipartFile docFile,
                                  Model model) {
        try {
            templateService.updateRevision(revisionId, attribute1, attribute2, docFile);
            return "redirect:/document-templates/" + id + "?updated=true";
        } catch (IOException | IllegalArgumentException e) {
            model.addAttribute("error", "Fehler beim Aktualisieren: " + e.getMessage());
            return viewTemplate(id, model);
        }
    }

    // ─────────────────────────────────────────────
    // DOWNLOAD Template-Datei
    // ─────────────────────────────────────────────

    @GetMapping("/{id}/revisions/{revisionId}/download")
    public ResponseEntity<Resource> downloadTemplate(@PathVariable String id,
                                                      @PathVariable Long revisionId) {
        Resource resource = templateService.loadDocFile(revisionId);
        DocumentTemplateRevision revision = templateService.getRevision(revisionId);
        String fileName = revision.getDocFileName() != null
                ? revision.getDocFileName() : "template.doc";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }
}
