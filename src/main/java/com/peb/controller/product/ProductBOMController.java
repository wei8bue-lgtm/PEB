// src/main/java/com/peb/controller/product/ProductBOMController.java
package com.peb.controller.product;

import com.peb.model.product.ProductBOM;
import com.peb.model.product.ProductBOMRevision;
import com.peb.model.product.ManufacturingStatus;
import com.peb.repository.product.ManufacturingBOMRepository;
import com.peb.repository.product.ProductBOMRepository;
import com.peb.repository.product.ProductDataRepository;
import com.peb.service.product.ProductBOMService;
import com.peb.service.product.ProductBOMStructureSnapshotService;
import com.peb.service.product.RevisionChainService;
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

    private final ProductBOMService          service;
    private final ProductBOMRepository       bomRepo;
    private final ProductDataRepository      pdRepo;
    private final ManufacturingBOMRepository mfgRepo;
    private final RevisionChainService       revisionChainService;
        private final ProductBOMStructureSnapshotService snapshotService;

    // ── Liste ────────────────────────────────────────────────────
    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            Model model) {

        List<ProductBOM> items =
                (search != null && !search.isBlank())
                ? bomRepo.findByNameContainingIgnoreCase(search)
                : bomRepo.findAll();

        model.addAttribute("bomList", items);
        model.addAttribute("search", search);
        return "product-bom/list";
    }

    // ── Neu (Formular) ───────────────────────────────────────────
    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createForm(Model model) {
        model.addAttribute("bom", new ProductBOM());
        model.addAttribute("availableProductData",
                pdRepo.findUnassigned());
        model.addAttribute("isEdit", false);
        return "product-bom/form";
    }

    // ── Neu (Speichern) ──────────────────────────────────────────
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
                    "✅ ProductBOM '" + saved.getName()
                    + "' erstellt.");
            return "redirect:/product-bom/" + saved.getId();
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/product-bom/new";
        }
    }

    // ── Detail ───────────────────────────────────────────────────
	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, Model model) {

		ProductBOM bom = service.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(
						"ProductBOM nicht gefunden: " + id));

		// ── Neueste Revision ──────────────────────────────────────
		ProductBOMRevision latestRevision = bom.getLatestRevision();
		model.addAttribute("latestRevision", latestRevision);

		String statusName = (latestRevision != null
							 && latestRevision.getStatus() != null)
				? latestRevision.getStatus().name()
				: "KEINE_REVISION";
		model.addAttribute("statusName", statusName);

		model.addAttribute("bom", bom);

		// ── Dropdown: unzugewiesene PDs laden ─────────────────────
		// findUnassigned() gibt PDs ohne BOM zurück.
		// Die aktuell zugewiesene PD wird im Template
		// separat als erste Option angezeigt.
		model.addAttribute("availableProductData",
				pdRepo.findUnassigned());

		model.addAttribute("availableMfgBOMs", mfgRepo.findAll());

		// ── Revisionskette ────────────────────────────────────────
		model.addAttribute("revisionChain", bom.getRevisions());
                model.addAttribute("importedStructureTree", snapshotService.getSnapshotTree(id));

		return "product-bom/detail";
	}

    // ── Edit (Formular) ──────────────────────────────────────────
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editForm(@PathVariable Long id, Model model) {

        ProductBOM bom = bomRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductBOM nicht gefunden: " + id));

        // ── statusName für Form-Template ──────────────────────
        ProductBOMRevision latestRevision = bom.getLatestRevision();
        String statusName = (latestRevision != null
                             && latestRevision.getStatus() != null)
                ? latestRevision.getStatus().name()
                : "KEINE_REVISION";

        model.addAttribute("bom",        bom);
        model.addAttribute("isEdit",     true);
        model.addAttribute("statusName", statusName);
        model.addAttribute("latestRevision", latestRevision);
        model.addAttribute("availableProductData",
                pdRepo.findAll());
        return "product-bom/form";
    }

    // ── Edit (Speichern) ─────────────────────────────────────────
    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editSave(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String bomVersion,
            @RequestParam(required = false) String description,
            RedirectAttributes ra) {

        ProductBOM bom = bomRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductBOM nicht gefunden: " + id));

        bom.setName(name);
        bom.setBomVersion(bomVersion != null ? bomVersion : "01");
        bom.setDescription(description);
        bomRepo.save(bom);

        ra.addFlashAttribute("successMessage",
                "✅ Product BOM '" + bom.getName()
                + "' aktualisiert.");
        return "redirect:/product-bom/" + id;
    }

    // ── Delete ───────────────────────────────────────────────────
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String delete(
            @PathVariable Long id,
            RedirectAttributes ra) {

        ProductBOM bom = bomRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductBOM nicht gefunden: " + id));

        boolean hasProductData = bom.getProductData() != null;
        boolean hasMfgBOMs     = !bom.getManufacturingBOMs()
                                      .isEmpty();

        if (hasProductData && hasMfgBOMs) {
            ra.addFlashAttribute("errorMessage",
                    "❌ Löschen nicht möglich: ProductData '"
                    + bom.getProductData().getName()
                    + "' und "
                    + bom.getManufacturingBOMs().size()
                    + " Mfg BOM(s) sind noch verknüpft.");
            return "redirect:/product-bom";
        }
        if (hasProductData) {
            ra.addFlashAttribute("errorMessage",
                    "❌ Löschen nicht möglich: ProductData '"
                    + bom.getProductData().getName()
                    + "' ist noch verknüpft.");
            return "redirect:/product-bom";
        }
        if (hasMfgBOMs) {
            ra.addFlashAttribute("errorMessage",
                    "❌ Löschen nicht möglich: "
                    + bom.getManufacturingBOMs().size()
                    + " Mfg BOM(s) sind noch verknüpft.");
            return "redirect:/product-bom";
        }

        String bomName = bom.getName();
        bomRepo.delete(bom);

        ra.addFlashAttribute("successMessage",
                "🗑️ Product BOM '" + bomName
                + "' erfolgreich gelöscht.");
        return "redirect:/product-bom";
    }

    // ── ProductData zuweisen ─────────────────────────────────────
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
            ra.addFlashAttribute("errorMessage",
                    e.getMessage());
        }
        return "redirect:/product-bom/" + id;
    }
}