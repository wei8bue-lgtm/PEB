package com.peb.controller;

import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.service.RevisionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Mixed MVC + REST controller for Revision operations
 */
@Controller
@RequestMapping("/api/revisions")
@RequiredArgsConstructor
public class RevisionController {

    private final RevisionService revisionService;

    // ── MVC: Redirect nach Design-View ───────────────────────────
    @PostMapping("/designs/{designId}/revise")
    @PreAuthorize("hasAuthority('WRITE')")
    public String reviseDesign(
            @PathVariable String designId,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        try {
            DesignRevision revision = revisionService.reviseDesign(
                    designId, authentication.getName());
            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ Neue Revision '"
                    + revision.getRevision()
                    + "' erfolgreich erstellt.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "❌ Revise fehlgeschlagen: " + e.getMessage());
        }

        return "redirect:/designs/" + designId;
    }

    // ── REST: Save As (wird per fetch/JS aufgerufen) ──────────────
    @PostMapping("/designs/{designId}/saveas")
    @PreAuthorize("hasAuthority('WRITE')")
    @ResponseBody
    public ResponseEntity<Design> saveAsDesign(
            @PathVariable String designId,
            @RequestParam String newName,
            Authentication authentication) {

        Design newDesign = revisionService.saveAs(
                designId, newName, authentication.getName());
        return ResponseEntity.ok(newDesign);
    }
}