// src/main/java/com/peb/controller/product/ProductBOPController.java
package com.peb.controller.product;

import com.peb.model.product.ManufacturingBOM;
import com.peb.model.product.ProductBOP;
import com.peb.repository.product.ManufacturingBOMRepository;
import com.peb.repository.product.ProductBOPRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/product-bop")
@RequiredArgsConstructor
public class ProductBOPController {

    private final ProductBOPRepository       bopRepo;
    private final ManufacturingBOMRepository mfgRepo;

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

    // ── Detail ───────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ProductBOP bop = bopRepo.findById(id).orElseThrow();
        model.addAttribute("bop",             bop);
        model.addAttribute("availableMfgBOMs", mfgRepo.findAll());
        return "product-bop/detail";
    }

    // ── Neu ──────────────────────────────────────────────────────
    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createForm(Model model) {
        model.addAttribute("bop", new ProductBOP());
        return "product-bop/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String create(
            @ModelAttribute ProductBOP bop,
            RedirectAttributes ra) {

        ProductBOP saved = bopRepo.save(bop);
        ra.addFlashAttribute("successMessage",
                "✅ Product BOP '" + saved.getName()
                + "' erstellt.");
        return "redirect:/product-bop/" + saved.getId();
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
}