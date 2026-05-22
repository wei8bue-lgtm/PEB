// src/main/java/com/peb/controller/product/ManufacturingBOMController.java
package com.peb.controller.product;

import com.peb.model.product.ManufacturingBOM;
import com.peb.model.product.ManufacturingOperation;
import com.peb.repository.product.ManufacturingBOMRepository;
import com.peb.repository.product.ManufacturingOperationRepository;
import com.peb.repository.product.ProductBOMRepository;
import com.peb.service.product.OperationPdfService;
import com.peb.service.product.ManufacturingBOMService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/manufacturing-bom")
@RequiredArgsConstructor
public class ManufacturingBOMController {
	
	    private static final org.slf4j.Logger log = 
            org.slf4j.LoggerFactory.getLogger(
                    ManufacturingBOMController.class);

    private final ManufacturingBOMRepository       mfgRepo;
    private final ManufacturingOperationRepository opRepo;
    private final ProductBOMRepository             pbomRepo;
    private final OperationPdfService              pdfService;
	private final ManufacturingBOMService  		   mfgService;
	

    // ── Liste ────────────────────────────────────────────────────
    @GetMapping
    public String list(
            @RequestParam(required = false) String search,
            Model model) {

        List<ManufacturingBOM> items =
                (search != null && !search.isBlank())
                ? mfgRepo.findByNameContainingIgnoreCase(search)
                : mfgRepo.findAll();

        model.addAttribute("mfgBomList", items);
        model.addAttribute("search", search);
        return "manufacturing-bom/list";
    }

    // ── Detail ───────────────────────────────────────────────────
	@GetMapping("/{id}")
	public String detail(@PathVariable Long id, Model model) {

		// Service-Methode nutzt @Transactional und löst das Merge-Problem
		ManufacturingBOM bom = mfgService.findByIdWithAllDetails(id);

		model.addAttribute("mfgBOM",              bom);
		model.addAttribute("availableProductBOMs", pbomRepo.findAll());
		model.addAttribute("newOperation",         new ManufacturingOperation());
		model.addAttribute("operationTypes",
				ManufacturingOperation.OperationType.values());

		return "manufacturing-bom/detail";
}

    // ── Neu (Formular) ───────────────────────────────────────────
    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createForm(Model model) {
        model.addAttribute("mfgBOM", new ManufacturingBOM());
        return "manufacturing-bom/form";
    }

    // ── Neu (Speichern) ──────────────────────────────────────────
@PostMapping("/{id}/operations/add")
@PreAuthorize("hasAuthority('WRITE')")
public String addOperation(
        @PathVariable Long id,
        @ModelAttribute ManufacturingOperation op,
        Authentication auth,
        RedirectAttributes ra) {

    // ── WICHTIGSTER FIX: ID auf null setzen → erzwingt INSERT ──
    op.setId(null);  // ← NEU! Verhindert UPDATE statt INSERT

    ManufacturingBOM bom = mfgRepo.findByIdOnly(id)
            .orElseThrow(() -> new IllegalArgumentException(
                    "ManufacturingBOM nicht gefunden: " + id));

    op.setManufacturingBOM(bom);
    op.setCreatedBy(auth.getName());

    if (op.getSequenceNumber() == null) {
        List<ManufacturingOperation> existing =
                opRepo.findByManufacturingBOMIdOrderBySequenceNumber(id);
        int max = existing.stream()
                .mapToInt(o -> o.getSequenceNumber() != null
                        ? o.getSequenceNumber() : 0)
                .max().orElse(0);
        op.setSequenceNumber(max + 10);
    }

    log.info("=== Speichere Operation ===");
    log.info("BOM ID:     {}", bom.getId());
    log.info("Op Name:    {}", op.getName());
    log.info("Op ID vor Save: {}", op.getId()); // Sollte null sein
    
    		// ── TEMPORÄRES DEBUGGING ─────────────────────────
/* 		log.info("=== Speichere Operation ===");
		log.info("BOM ID:     {}", bom.getId());
		log.info("Op Name:    {}", op.getName());
		log.info("Op Seq:     {}", op.getSequenceNumber());
		log.info("Op BOM-Ref: {}", op.getManufacturingBOM().getId()); */
		// ─────────────────────────────────────────────────

		ManufacturingOperation saved = opRepo.save(op);

		// ── TEMPORÄRES DEBUGGING ─────────────────────────
/* 		log.info("Gespeichert mit ID: {}", saved.getId()); */
    
    log.info("Gespeichert mit ID: {}", saved.getId()); // Sollte neue ID sein

    ra.addFlashAttribute("successMessage",
            "✅ Operation '" + op.getName() + "' hinzugefügt.");
    return "redirect:/manufacturing-bom/" + id;
}

    // ── Operation löschen ────────────────────────────────────────
    @PostMapping("/{id}/operations/{opId}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String deleteOperation(
            @PathVariable Long id,
            @PathVariable Long opId,
            RedirectAttributes ra) {

        opRepo.deleteById(opId);
        ra.addFlashAttribute("successMessage",
                "🗑️ Operation gelöscht.");
        return "redirect:/manufacturing-bom/" + id;
    }

    // ── ProductBOM hinzufügen ────────────────────────────────────
    @PostMapping("/{id}/product-boms/add")
    @PreAuthorize("hasAuthority('WRITE')")
    public String addProductBOM(
            @PathVariable Long id,
            @RequestParam Long productBomId,
            RedirectAttributes ra) {

        // Mit ProductBOMs laden (für contains-Check)
        ManufacturingBOM mfg = mfgRepo
                .findByIdWithProductBOMs(id)
                .orElseThrow();

        pbomRepo.findById(productBomId).ifPresent(pb -> {
            if (!mfg.getProductBOMs().contains(pb)) {
                mfg.getProductBOMs().add(pb);
                mfgRepo.save(mfg);
            }
        });

        ra.addFlashAttribute("successMessage",
                "✅ ProductBOM hinzugefügt.");
        return "redirect:/manufacturing-bom/" + id;
    }

    // ── ProductBOM entfernen ─────────────────────────────────────
    @PostMapping("/{id}/product-boms/{pbId}/remove")
    @PreAuthorize("hasAuthority('DELETE')")
    public String removeProductBOM(
            @PathVariable Long id,
            @PathVariable Long pbId,
            RedirectAttributes ra) {

        ManufacturingBOM mfg = mfgRepo
                .findByIdWithProductBOMs(id)
                .orElseThrow();
        mfg.getProductBOMs()
           .removeIf(pb -> pb.getId().equals(pbId));
        mfgRepo.save(mfg);

        ra.addFlashAttribute("successMessage",
                "🗑️ ProductBOM entfernt.");
        return "redirect:/manufacturing-bom/" + id;
    }

    // ── Arbeitsanweisung (HTML) ──────────────────────────────────
    @GetMapping("/{id}/work-instruction")
    public ResponseEntity<byte[]> workInstruction(
            @PathVariable Long id) {

        String html = pdfService.generateWorkInstructionHtml(id);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        "text/html; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"arbeitsanweisung-"
                        + id + ".html\"")
                .body(bytes);
    }
}