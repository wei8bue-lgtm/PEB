// src/main/java/com/peb/controller/product/ProductBOMController.java
package com.peb.controller.product;

import com.peb.model.product.ProductBOM;
import com.peb.repository.product.ManufacturingBOMRepository;
import com.peb.repository.product.ProductBOMRepository;
import com.peb.repository.product.ProductDataRepository;
import com.peb.service.product.ProductBOMService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/product-bom")
@RequiredArgsConstructor
public class ProductBOMController {

    private final ProductBOMService       service;
    private final ProductBOMRepository    bomRepo;
    private final ProductDataRepository   pdRepo;
    private final ManufacturingBOMRepository mfgRepo;

    @GetMapping
    public String list(@RequestParam(required = false) String search,
                       Model model) {
        List<ProductBOM> items = (search != null && !search.isBlank())
                ? bomRepo.findByNameContainingIgnoreCase(search)
                : bomRepo.findAll();
        model.addAttribute("bomList", items);
        return "product-bom/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("bom",
                service.findById(id).orElseThrow());
        // Nur nicht zugewiesene ProductData anbieten
        model.addAttribute("availableProductData",
                pdRepo.findUnassigned());
        model.addAttribute("availableMfgBOMs",
                mfgRepo.findAll());
        return "product-bom/detail";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createForm(Model model) {
        model.addAttribute("bom", new ProductBOM());
        model.addAttribute("availableProductData",
                pdRepo.findAll());
        return "product-bom/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String create(
            @ModelAttribute ProductBOM bom,
            @RequestParam(required = false) String productDataId,
            Authentication auth,
            RedirectAttributes ra) {
        try {
            ProductBOM saved = service.create(bom, productDataId);
            ra.addFlashAttribute("successMessage",
                    "✅ ProductBOM erstellt.");
            return "redirect:/product-bom/" + saved.getId();
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/product-bom/new";
        }
    }

    // ProductData zuweisen / ändern
    @PostMapping("/{id}/assign-product-data")
    @PreAuthorize("hasAuthority('WRITE')")
    public String assignProductData(
            @PathVariable Long id,
            @RequestParam(required = false) String productDataId,
            RedirectAttributes ra) {
        try {
            service.assignProductData(id, productDataId);
            ra.addFlashAttribute("successMessage",
                    "✅ ProductData zugewiesen.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/product-bom/" + id;
    }
}