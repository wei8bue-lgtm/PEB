// src/main/java/com/peb/controller/product/ProductDataController.java
package com.peb.controller.product;

import com.peb.model.product.ProductData;
import com.peb.service.product.ProductDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/product-data")
@RequiredArgsConstructor
public class ProductDataController {

    private final ProductDataService service;

    // ── Liste ────────────────────────────────────────────────────
    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            Model model) {
        List<ProductData> items = service.search(search);
        model.addAttribute("productDataList", items);
        model.addAttribute("search", search);
        return "product-data/list";
    }

    // ── Detail ───────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        ProductData pd = service.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductData nicht gefunden: " + id));
        model.addAttribute("productData", pd);
        return "product-data/detail";
    }

    // ── Neu: GET ─────────────────────────────────────────────────
    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createForm(Model model) {
        // WICHTIG: Attributname "productData" muss exakt mit
        // th:object="${productData}" im Template übereinstimmen
        model.addAttribute("productData", new ProductData());
        model.addAttribute("statuses",
                ProductData.ProductDataStatus.values());
        model.addAttribute("isNew", true);
        return "product-data/form";
    }

    // ── Neu: POST ────────────────────────────────────────────────
    @PostMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String create(
            // @ModelAttribute Name MUSS = th:object Name sein
            @Valid @ModelAttribute("productData")
                    ProductData productData,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes ra) {

        // Validation-Fehler → Form erneut anzeigen
        if (bindingResult.hasErrors()) {
            // Model-Attribute erneut setzen!
            model.addAttribute("statuses",
                    ProductData.ProductDataStatus.values());
            model.addAttribute("isNew", true);
            // productData ist bereits im Model durch @ModelAttribute
            return "product-data/form";
        }

        ProductData saved = service.create(productData);
        ra.addFlashAttribute("successMessage",
                "✅ ProductData " + saved.getId()
                + " erfolgreich erstellt.");
        return "redirect:/product-data/" + saved.getId();
    }

    // ── Bearbeiten: GET ──────────────────────────────────────────
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editForm(@PathVariable String id, Model model) {
        ProductData pd = service.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductData nicht gefunden: " + id));
        model.addAttribute("productData", pd);
        model.addAttribute("statuses",
                ProductData.ProductDataStatus.values());
        model.addAttribute("isNew", false);
        return "product-data/form";
    }

    // ── Bearbeiten: POST ─────────────────────────────────────────
    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String update(
            @PathVariable String id,
            @Valid @ModelAttribute("productData")
                    ProductData productData,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes ra) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("statuses",
                    ProductData.ProductDataStatus.values());
            model.addAttribute("isNew", false);
            return "product-data/form";
        }

        service.update(id, productData);
        ra.addFlashAttribute("successMessage",
                "✅ ProductData erfolgreich aktualisiert.");
        return "redirect:/product-data/" + id;
    }

    // ── Löschen ──────────────────────────────────────────────────
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String delete(@PathVariable String id,
                         RedirectAttributes ra) {
        service.delete(id);
        ra.addFlashAttribute("successMessage",
                "🗑️ ProductData " + id + " gelöscht.");
        return "redirect:/product-data";
    }
}