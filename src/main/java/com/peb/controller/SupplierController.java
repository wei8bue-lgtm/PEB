package com.peb.controller;

import com.peb.dto.SupplierDTO;
import com.peb.service.SupplierService;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * MVC-Controller für Lieferantenmanagement.
 * URL-Struktur bewusst analog zu MaterialController:
 *
 *   GET  /suppliers                        – Lieferantenliste
 *   GET  /suppliers/new                    – Anlage-Formular
 *   POST /suppliers                        – Neuen Lieferanten speichern
 *   GET  /suppliers/{id}                   – Detailansicht (Stammdaten, Revisionen)
 *   GET  /suppliers/{id}/edit              – Bearbeiten-Formular
 *   POST /suppliers/{id}                   – Änderungen speichern
 *   POST /suppliers/{id}/delete            – Lieferant löschen (ADMIN)
 *   POST /suppliers/{id}/revisions         – Neue Revision anlegen
 *   POST /suppliers/revisions/{id}/release – Revision freigeben
 *   GET  /suppliers/api/search             – Live-Suche (AJAX)
 */
@Controller
@RequestMapping("/suppliers")
@RequiredArgsConstructor
@Slf4j
public class SupplierController {

    private final SupplierService supplierService;

    // ── Übersicht ────────────────────────────────────────────────

    @GetMapping
    public String list(Model model) {
        model.addAttribute("suppliers", supplierService.findAll());
        return "supplier/supplier-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        SupplierDTO supplier = supplierService.getSupplier(id);
        model.addAttribute("supplier", supplier);
        model.addAttribute("revisions", supplierService.getRevisions(id));
        return "supplier/supplier-detail";
    }

    // ── Anlage / Bearbeitung ─────────────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String newForm(Model model) {
        model.addAttribute("supplier", new SupplierDTO());
        model.addAttribute("isNew", true);
        return "supplier/supplier-form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE')")
    public String create(@ModelAttribute SupplierDTO dto, RedirectAttributes ra) {
        SupplierDTO saved = supplierService.createSupplier(dto);
        ra.addFlashAttribute("success", "Lieferant " + saved.getId() + " angelegt.");
        return "redirect:/suppliers/" + saved.getId();
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editForm(@PathVariable String id, Model model) {
        model.addAttribute("supplier", supplierService.getSupplier(id));
        model.addAttribute("isNew", false);
        return "supplier/supplier-form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String update(@PathVariable String id, @ModelAttribute SupplierDTO dto, RedirectAttributes ra) {
        supplierService.updateSupplier(id, dto);
        ra.addFlashAttribute("success", "Lieferant " + id + " aktualisiert.");
        return "redirect:/suppliers/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        supplierService.deleteSupplier(id);
        ra.addFlashAttribute("info", "Lieferant " + id + " gelöscht.");
        return "redirect:/suppliers";
    }

    // ── Revisionen ───────────────────────────────────────────────

    @PostMapping("/{id}/revisions")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createRevision(@PathVariable String id,
                                  @RequestParam String revision,
                                  @RequestParam(required = false) String changeDescription,
                                  RedirectAttributes ra) {
        supplierService.createRevision(id, revision, changeDescription, ReleaseStatus.WORKING);
        ra.addFlashAttribute("success", "Neue Revision " + revision + " angelegt.");
        return "redirect:/suppliers/" + id;
    }

    @PostMapping("/revisions/{revisionId}/release")
    @PreAuthorize("hasAuthority('WRITE')")
    public String releaseRevision(@PathVariable Long revisionId,
                                   @RequestParam String supplierId,
                                   RedirectAttributes ra) {
        try {
            supplierService.releaseRevision(revisionId);
            ra.addFlashAttribute("success", "Revision freigegeben.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/suppliers/" + supplierId;
    }

    // ── REST: Live-Suche (analog /materials/api/search) ──────────

    @GetMapping("/api/search")
    @ResponseBody
    public List<SupplierDTO> apiSearch(@RequestParam String term) {
        return supplierService.search(term);
    }
}
