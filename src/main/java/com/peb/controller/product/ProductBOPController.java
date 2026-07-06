// src/main/java/com/peb/controller/product/ProductBOPController.java
package com.peb.controller.product;

import com.peb.model.product.ManufacturingStatus;
import com.peb.model.product.ProductBOP;
import com.peb.model.product.ProductBOPRevision;
import com.peb.repository.product.ManufacturingBOMRepository;
import com.peb.repository.product.ProductBOPRepository;
import com.peb.repository.product.ProductBOPRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.peb.service.product.RevisionChainService;

import java.util.List;

@Controller
@RequestMapping("/product-bop")
@RequiredArgsConstructor
public class ProductBOPController {

    private final ProductBOPRepository         bopRepo;
    private final ProductBOPRevisionRepository bopRevRepo;
    private final ManufacturingBOMRepository   mfgRepo;
    private final RevisionChainService         revisionChainService;

    // ── Liste ────────────────────────────────────────────────────
    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            Model model) {

        List<ProductBOP> items =
                (search != null && !search.isBlank())
                ? bopRepo.findByNameContainingIgnoreCase(search)
                : bopRepo.findAll();

        model.addAttribute("bopList", items);
        model.addAttribute("search",  search);
        return "product-bop/list";
    }

    // ── Neu (Formular) ───────────────────────────────────────────
    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createForm(Model model) {
        model.addAttribute("bop",    new ProductBOP());
        model.addAttribute("isEdit", false);
        return "product-bop/form";
    }

    // ── Neu (Speichern) ──────────────────────────────────────────
    @PostMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String create(
            @ModelAttribute ProductBOP bop,
            RedirectAttributes ra) {

        ProductBOP saved = bopRepo.save(bop);

        // ── Initiale Revision 01 automatisch erstellen ────────
        ProductBOPRevision initRev = new ProductBOPRevision();
        initRev.setProductBOP(saved);
        initRev.setRevision("01");
        initRev.setStatus(ManufacturingStatus.WORKING);
        initRev.setCreatedBy(currentUser());
        bopRevRepo.save(initRev);

        ra.addFlashAttribute("successMessage",
                "✅ Product BOP '" + saved.getName()
                + "' erstellt.");
        return "redirect:/product-bop/" + saved.getId();
    }

    // ── Detail ───────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {

        ProductBOP bop = bopRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductBOP nicht gefunden: " + id));

        // ── Neueste Revision ──────────────────────────────────
        ProductBOPRevision latestRevision =
                bop.getLatestRevision();
        model.addAttribute("latestRevision", latestRevision);

        // ── statusName IMMER als String ins Model ─────────────
        String statusName = (latestRevision != null
                             && latestRevision.getStatus() != null)
                ? latestRevision.getStatus().name()
                : "KEINE_REVISION";
        model.addAttribute("statusName", statusName);

        model.addAttribute("bop", bop);
        model.addAttribute("availableMfgBOMs",
                mfgRepo.findAll());

        // ── Revisionskette ────────────────────────────────────
        List<ProductBOPRevision> revisionChain =
                bop.getRevisions();
        model.addAttribute("revisionChain", revisionChain);

        return "product-bop/detail";
    }

    // ── Edit (Formular) ──────────────────────────────────────────
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editForm(@PathVariable Long id, Model model) {

        ProductBOP bop = bopRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductBOP nicht gefunden: " + id));

        ProductBOPRevision latestRevision =
                bop.getLatestRevision();
        String statusName = (latestRevision != null
                             && latestRevision.getStatus() != null)
                ? latestRevision.getStatus().name()
                : "KEINE_REVISION";

        model.addAttribute("bop",         bop);
        model.addAttribute("isEdit",      true);
        model.addAttribute("statusName",  statusName);
        model.addAttribute("latestRevision", latestRevision);
        return "product-bop/form";
    }

    // ── Edit (Speichern) ─────────────────────────────────────────
    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editSave(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String bopVersion,
            @RequestParam(required = false) String description,
            RedirectAttributes ra) {

        ProductBOP bop = bopRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductBOP nicht gefunden: " + id));

        bop.setName(name);
        bop.setBopVersion(bopVersion != null ? bopVersion : "01");
        bop.setDescription(description);
        bopRepo.save(bop);

        ra.addFlashAttribute("successMessage",
                "✅ Product BOP '" + bop.getName()
                + "' aktualisiert.");
        return "redirect:/product-bop/" + id;
    }

    // ── Delete ───────────────────────────────────────────────────
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String delete(
            @PathVariable Long id,
            RedirectAttributes ra) {

        ProductBOP bop = bopRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductBOP nicht gefunden: " + id));

        if (!bop.getManufacturingBOMs().isEmpty()) {
            ra.addFlashAttribute("errorMessage",
                    "❌ Löschen nicht möglich: '"
                    + bop.getName() + "' ist mit "
                    + bop.getManufacturingBOMs().size()
                    + " Manufacturing BOM(s) verknüpft.");
            return "redirect:/product-bop";
        }

        String name = bop.getName();
        bopRepo.delete(bop);
        ra.addFlashAttribute("successMessage",
                "🗑️ Product BOP '" + name
                + "' erfolgreich gelöscht.");
        return "redirect:/product-bop";
    }

    // ── Manufacturing BOM hinzufügen ─────────────────────────────
    @PostMapping("/{id}/mfg-boms/add")
    @PreAuthorize("hasAuthority('WRITE')")
    public String addMfgBOM(
            @PathVariable Long id,
            @RequestParam Long mfgBomId,
            RedirectAttributes ra) {

        ProductBOP bop = bopRepo.findById(id).orElseThrow();
        mfgRepo.findById(mfgBomId).ifPresent(mfg -> {
            if (!bop.getManufacturingBOMs().contains(mfg)) {
                bop.getManufacturingBOMs().add(mfg);
                bopRepo.save(bop);
            }
        });
        ra.addFlashAttribute("successMessage",
                "✅ Manufacturing BOM hinzugefügt.");
        return "redirect:/product-bop/" + id;
    }

    // ── Manufacturing BOM entfernen ──────────────────────────────
    @PostMapping("/{id}/mfg-boms/{mfgId}/remove")
    @PreAuthorize("hasAuthority('DELETE')")
    public String removeMfgBOM(
            @PathVariable Long id,
            @PathVariable Long mfgId,
            RedirectAttributes ra) {

        ProductBOP bop = bopRepo.findById(id).orElseThrow();
        bop.getManufacturingBOMs()
           .removeIf(m -> m.getId().equals(mfgId));
        bopRepo.save(bop);
        ra.addFlashAttribute("successMessage",
                "🗑️ Manufacturing BOM entfernt.");
        return "redirect:/product-bop/" + id;
    }

    // ── Hilfsmethode ─────────────────────────────────────────────
    private String currentUser() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}