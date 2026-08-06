package com.peb.controller;

import com.peb.dto.MaterialComplianceDTO;
import com.peb.dto.MaterialDTO;
import com.peb.dto.MaterialSupplierDTO;
import com.peb.service.EclassService;
import com.peb.service.MaterialService;
import com.peb.util.ObjectType;
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
 * MVC-Controller für Materialmanagement.
 *
 * URL-Struktur:
 *   GET  /materials                       – Materialliste
 *   GET  /materials/new                   – Anlage-Formular
 *   POST /materials                       – Neues Material speichern
 *   GET  /materials/{id}                  – Detailansicht (Stammdaten, Revisionen, Compliance, Lieferanten)
 *   GET  /materials/{id}/edit             – Bearbeiten-Formular
 *   POST /materials/{id}                  – Änderungen speichern
 *   POST /materials/{id}/delete           – Material löschen (ADMIN)
 *   POST /materials/{id}/revisions        – Neue Revision anlegen
 *   POST /materials/revisions/{id}/release – Revision freigeben
 *   POST /materials/{id}/compliance       – Compliance-Daten speichern
 *   POST /materials/{id}/suppliers        – Lieferant hinzufügen
 *   POST /materials/suppliers/{id}/delete – Lieferant entfernen
 *   GET  /materials/api/search            – Live-Suche (AJAX)
 *
 * Die Klassifizierung eines Materials über ECLASS läuft weiterhin über
 * die bestehenden Endpunkte /eclass/classify/MATERIAL/{id} und
 * /eclass/similar/MATERIAL/{id} (EclassController, unverändert).
 */
@Controller
@RequestMapping("/materials")
@RequiredArgsConstructor
@Slf4j
public class MaterialController {

    private final MaterialService materialService;
    private final EclassService eclassService;

    // ── Übersicht ────────────────────────────────────────────────

    @GetMapping
    public String list(Model model) {
        model.addAttribute("materials", materialService.findAll());
        return "material/material-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        MaterialDTO material = materialService.getMaterial(id);
        model.addAttribute("material", material);
        model.addAttribute("revisions", materialService.getRevisions(id));
        model.addAttribute("suppliers", materialService.getSuppliers(id));
        model.addAttribute("classification", eclassService.getClassification(id, ObjectType.MATERIAL));
        return "material/material-detail";
    }

    // ── Anlage / Bearbeitung ─────────────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String newForm(Model model) {
        model.addAttribute("material", new MaterialDTO());
        model.addAttribute("isNew", true);
        return "material/material-form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE')")
    public String create(@ModelAttribute MaterialDTO dto, RedirectAttributes ra) {
        MaterialDTO saved = materialService.createMaterial(dto);
        ra.addFlashAttribute("success", "Material " + saved.getId() + " angelegt.");
        return "redirect:/materials/" + saved.getId();
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editForm(@PathVariable String id, Model model) {
        model.addAttribute("material", materialService.getMaterial(id));
        model.addAttribute("isNew", false);
        return "material/material-form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String update(@PathVariable String id, @ModelAttribute MaterialDTO dto, RedirectAttributes ra) {
        materialService.updateMaterial(id, dto);
        ra.addFlashAttribute("success", "Material " + id + " aktualisiert.");
        return "redirect:/materials/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable String id, RedirectAttributes ra) {
        materialService.deleteMaterial(id);
        ra.addFlashAttribute("info", "Material " + id + " gelöscht.");
        return "redirect:/materials";
    }

    // ── Revisionen ───────────────────────────────────────────────

    @PostMapping("/{id}/revisions")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createRevision(@PathVariable String id,
                                  @RequestParam String revision,
                                  @RequestParam(required = false) String changeDescription,
                                  RedirectAttributes ra) {
        materialService.createRevision(id, revision, changeDescription, ReleaseStatus.WORKING);
        ra.addFlashAttribute("success", "Neue Revision " + revision + " angelegt.");
        return "redirect:/materials/" + id;
    }

    @PostMapping("/revisions/{revisionId}/release")
    @PreAuthorize("hasAuthority('WRITE')")
    public String releaseRevision(@PathVariable Long revisionId,
                                   @RequestParam String materialId,
                                   RedirectAttributes ra) {
        try {
            materialService.releaseRevision(revisionId);
            ra.addFlashAttribute("success", "Revision freigegeben.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/materials/" + materialId;
    }

    // ── Compliance ───────────────────────────────────────────────

    @PostMapping("/{id}/compliance")
    @PreAuthorize("hasAuthority('WRITE')")
    public String saveCompliance(@PathVariable String id,
                                  @ModelAttribute MaterialComplianceDTO dto,
                                  RedirectAttributes ra) {
        materialService.saveCompliance(id, dto);
        ra.addFlashAttribute("success", "Compliance-Daten gespeichert.");
        return "redirect:/materials/" + id;
    }

    // ── Lieferanten ──────────────────────────────────────────────

    @PostMapping("/{id}/suppliers")
    @PreAuthorize("hasAuthority('WRITE')")
    public String addSupplier(@PathVariable String id,
                               @ModelAttribute MaterialSupplierDTO dto,
                               RedirectAttributes ra) {
        materialService.addSupplier(id, dto);
        ra.addFlashAttribute("success", "Lieferant hinzugefügt.");
        return "redirect:/materials/" + id;
    }

    @PostMapping("/suppliers/{supplierId}/delete")
    @PreAuthorize("hasAuthority('WRITE')")
    public String removeSupplier(@PathVariable Long supplierId,
                                  @RequestParam String materialId,
                                  RedirectAttributes ra) {
        materialService.removeSupplier(supplierId);
        ra.addFlashAttribute("info", "Lieferant entfernt.");
        return "redirect:/materials/" + materialId;
    }

    // ── REST: Live-Suche (analog /api/v1/eclass/search) ─────────

    @GetMapping("/api/search")
    @ResponseBody
    public List<MaterialDTO> apiSearch(@RequestParam String term) {
        return materialService.search(term);
    }
}
