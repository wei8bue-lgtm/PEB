// src/main/java/com/peb/controller/product/ProductDataController.java
package com.peb.controller.product;

import com.peb.model.product.ProductData;
import com.peb.model.product.ProductDataRevision;
import com.peb.service.product.ProductDataService;
import com.peb.service.product.RevisionChainService;
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

    private final ProductDataService   service;
    private final RevisionChainService revisionChainService;

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
// ProductDataController.java

		@GetMapping("/{id}")
		public String detail(@PathVariable String id, Model model) {
			String trimmedId = id.trim();

			ProductData pd = service.findById(trimmedId)
					.orElseThrow(() -> new IllegalArgumentException(
							"ProductData nicht gefunden: " + trimmedId));

			model.addAttribute("productData", pd);

			List<ProductDataRevision> revisionChain =
					revisionChainService
							.getRevisionsForProductData(trimmedId);
			model.addAttribute("revisionChain", revisionChain);

			ProductDataRevision latestRevision = pd.getLatestRevision();
			model.addAttribute("latestRevision", latestRevision);

			// ── statusName IMMER als String ins Model ─────────────────
			// Verhindert jeden null / .name()-Fehler im Template
			String statusName = (latestRevision != null
								 && latestRevision.getStatus() != null)
					? latestRevision.getStatus().name()
					: "KEINE_REVISION";
			model.addAttribute("statusName", statusName);

			return "product-data/detail";
		}

    // ── Neu: GET ─────────────────────────────────────────────────
    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createForm(Model model) {
        model.addAttribute("productData", new ProductData());
        model.addAttribute("isNew", true);
        return "product-data/form";
    }

    // ── Neu: POST ────────────────────────────────────────────────
    @PostMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String create(
            @Valid @ModelAttribute("productData")
                    ProductData productData,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes ra) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("isNew", true);
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
    public String editForm(@PathVariable String id,
                           Model model) {
        String trimmedId = id.trim();

        ProductData pd = service.findById(trimmedId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductData nicht gefunden: "
                        + trimmedId));

        model.addAttribute("productData", pd);
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

        String trimmedId = id.trim();

        if (bindingResult.hasErrors()) {
            model.addAttribute("isNew", false);
            return "product-data/form";
        }

        service.update(trimmedId, productData);
        ra.addFlashAttribute("successMessage",
                "✅ ProductData erfolgreich aktualisiert.");
        return "redirect:/product-data/" + trimmedId;
    }

    // ── Löschen ──────────────────────────────────────────────────
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String delete(@PathVariable String id,
                         RedirectAttributes ra) {
        String trimmedId = id.trim();

        ProductData pd = service.findById(trimmedId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductData nicht gefunden: "
                        + trimmedId));

        if (pd.getProductBOM() != null) {
            ra.addFlashAttribute("errorMessage",
                    "❌ Löschen nicht möglich: ProductData '"
                    + pd.getName()
                    + "' ist mit Product BOM '"
                    + pd.getProductBOM().getName()
                    + "' verknüpft.");
            return "redirect:/product-data";
        }

        service.delete(trimmedId);
        ra.addFlashAttribute("successMessage",
                "🗑️ ProductData " + trimmedId + " gelöscht.");
        return "redirect:/product-data";
    }
}