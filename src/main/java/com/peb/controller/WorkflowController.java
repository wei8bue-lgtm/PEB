// src/main/java/com/peb/controller/WorkflowController.java
package com.peb.controller;

import com.peb.model.cad.DrawingRevision;
import com.peb.model.ebom.EBOMRevision;
import com.peb.service.DrawingRevisionService;
import com.peb.service.DrawingWorkflowService;
import com.peb.service.EBOMRevisionService;
import com.peb.service.EBOMWorkflowService;
import com.peb.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Mixed MVC + REST controller for Workflow operations
 * Covers: Design, Drawing, EBOM
 */
@Controller
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService       workflowService;
    private final EBOMWorkflowService   ebomWorkflowService;
    private final EBOMRevisionService   ebomRevisionService;
    private final DrawingWorkflowService drawingWorkflowService;   // ← NEU
    private final DrawingRevisionService drawingRevisionService;   // ← NEU

    // ══════════════════════════════════════════════════════════════════════════
    // DESIGN
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/designs/{designId}/revisions/{revisionId}/release")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String releaseDesignRevision(
            @PathVariable String designId,
            @PathVariable Long   revisionId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            workflowService.releaseDesignRevision(
                    designId, revisionId, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ Revision erfolgreich released.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "❌ Release fehlgeschlagen: " + e.getMessage());
        }
        return "redirect:/designs/" + designId;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAWING – Release (MVC redirect)
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/drawings/{drawingId}/revisions/{revisionId}/release")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String releaseDrawingRevision(
            @PathVariable String drawingId,
            @PathVariable Long   revisionId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            drawingWorkflowService.releaseDrawingRevision(
                    drawingId, revisionId, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ Drawing Revision erfolgreich released.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "❌ Release fehlgeschlagen: " + e.getMessage());
        }
        return "redirect:/drawings/" + drawingId;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAWING – Revise (MVC redirect)
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/drawings/{drawingId}/revise")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public String reviseDrawing(
            @PathVariable String drawingId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            DrawingRevision newRev = drawingRevisionService.reviseDrawing(
                    drawingId, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ Neue Revision " + newRev.getRevision() + " erstellt.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "❌ Revise fehlgeschlagen: " + e.getMessage());
        }
        return "redirect:/drawings/" + drawingId;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAWING – Save As (REST → JSON, wie Design)
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/drawings/{drawingId}/saveas")
    @PreAuthorize("hasAuthority('WRITE')")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, String>> saveAsDrawing(
            @PathVariable String drawingId,
            @RequestParam(required = false) String newName,
            Authentication authentication) {

        try {
            var copy = drawingRevisionService.saveAsDrawing(
                    drawingId, newName, authentication.getName());
            return ResponseEntity.ok(
                    java.util.Map.of(
                            "id",      copy.getId(),
                            "name",    copy.getName(),
                            "message", "Drawing copied successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // EBOM
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/ebom/{objectId}/release")
    @PreAuthorize("hasAuthority('EXECUTE')")
    @ResponseBody
    public ResponseEntity<String> releaseEBOMRevision(
            @PathVariable String objectId,
            Authentication authentication) {
        try {
            ebomWorkflowService.releaseEBOMRevision(
                    objectId, authentication.getName());
            return ResponseEntity.accepted().body(
                    "Release workflow started. You will receive an email notification.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    "Release failed: " + e.getMessage());
        }
    }

    @PostMapping("/ebom/{objectId}/revise")
    @PreAuthorize("hasAuthority('EXECUTE')")
    @ResponseBody
    public ResponseEntity<String> reviseEBOMObject(
            @PathVariable String objectId,
            Authentication authentication) {
        try {
            EBOMRevision newRevision = ebomRevisionService.reviseEBOMObject(
                    objectId, authentication.getName());
            return ResponseEntity.ok("New revision "
                    + newRevision.getRevision() + " created successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    "Failed to create revision: " + e.getMessage());
        }
    }
}