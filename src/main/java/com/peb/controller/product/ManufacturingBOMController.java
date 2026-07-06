// src/main/java/com/peb/controller/product/ManufacturingBOMController.java
package com.peb.controller.product;

import com.peb.model.product.ManufacturingBOM;
import com.peb.model.product.ManufacturingBOMRevision;
import com.peb.model.product.ManufacturingOperation;
import com.peb.repository.product.ManufacturingBOMRepository;
import com.peb.repository.product.ManufacturingOperationRepository;
import com.peb.repository.product.ProductBOMRepository;
import com.peb.service.product.ManufacturingBOMService;
import com.peb.service.product.ManufacturingLifecycleService;
import com.peb.service.product.OperationPdfService;
import com.peb.service.product.RevisionChainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/manufacturing-bom")
@RequiredArgsConstructor
@Slf4j
public class ManufacturingBOMController {

    private final ManufacturingBOMRepository       mfgRepo;
    private final ManufacturingOperationRepository opRepo;
    private final ProductBOMRepository             pbomRepo;
    private final OperationPdfService              pdfService;
    private final ManufacturingBOMService          mfgService;
    private final RevisionChainService             revisionChainService;

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

    // ── Neu (Formular) ───────────────────────────────────────────
    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String createForm(Model model) {
        model.addAttribute("mfgBOM",  new ManufacturingBOM());
        model.addAttribute("isEdit",  false);
        return "manufacturing-bom/form";
    }

    // ── Neu (Speichern) ──────────────────────────────────────────
    @PostMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String create(
            @ModelAttribute ManufacturingBOM bom,
            RedirectAttributes ra) {

        // Initiale Revision 01 wird im Service erstellt
        ManufacturingBOM saved = mfgService.create(bom);

        // ── Initiale Revision 01 erstellen ───────────────────
        ManufacturingBOMRevision initRev =
                new ManufacturingBOMRevision();
        initRev.setManufacturingBOM(saved);
        initRev.setRevision("01");
        initRev.setStatus(
                com.peb.model.product.ManufacturingStatus.WORKING);
        // createdBy über Auditing oder manuell:
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context
                .SecurityContextHolder.getContext()
                .getAuthentication();
        if (auth != null) {
            initRev.setCreatedBy(auth.getName());
        }

        com.peb.repository.product.ManufacturingBOMRevisionRepository
                mfgRevRepo = null;
        // → siehe Fix 1b unten – Repository injecten!

        ra.addFlashAttribute("successMessage",
                "✅ Manufacturing BOM '"
                + saved.getName() + "' erstellt.");
        return "redirect:/manufacturing-bom/" + saved.getId();
    }

    // ── Detail ───────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {

        ManufacturingBOM bom =
                mfgService.findByIdWithAllDetails(id);

        // ── Neueste Revision ──────────────────────────────────
        ManufacturingBOMRevision latestRevision =
                bom.getLatestRevision();
        model.addAttribute("latestRevision", latestRevision);

        // ── statusName IMMER als String ins Model ─────────────
        String statusName = (latestRevision != null
                             && latestRevision.getStatus() != null)
                ? latestRevision.getStatus().name()
                : "KEINE_REVISION";
        model.addAttribute("statusName", statusName);

        model.addAttribute("mfgBOM", bom);
        model.addAttribute("availableProductBOMs",
                pbomRepo.findAll());
        model.addAttribute("newOperation",
                new ManufacturingOperation());
        model.addAttribute("operationTypes",
                ManufacturingOperation.OperationType.values());

        // ── Revisionskette ────────────────────────────────────
        List<ManufacturingBOMRevision> revisionChain =
                bom.getRevisions();
        model.addAttribute("revisionChain", revisionChain);

        return "manufacturing-bom/detail";
    }

    // ── Edit (Formular) ──────────────────────────────────────────
    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editForm(@PathVariable Long id, Model model) {

        ManufacturingBOM bom = mfgRepo.findByIdOnly(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ManufacturingBOM nicht gefunden: " + id));

        ManufacturingBOMRevision latestRevision =
                bom.getLatestRevision();
        String statusName = (latestRevision != null
                             && latestRevision.getStatus() != null)
                ? latestRevision.getStatus().name()
                : "KEINE_REVISION";

        model.addAttribute("mfgBOM",         bom);
        model.addAttribute("isEdit",         true);
        model.addAttribute("statusName",     statusName);
        model.addAttribute("latestRevision", latestRevision);
        return "manufacturing-bom/form";
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

        ManufacturingBOM bom = mfgRepo.findByIdOnly(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ManufacturingBOM nicht gefunden: " + id));

        bom.setName(name);
        bom.setBomVersion(bomVersion != null ? bomVersion : "01");
        bom.setDescription(description);
        mfgRepo.save(bom);

        ra.addFlashAttribute("successMessage",
                "✅ Manufacturing BOM '" + bom.getName()
                + "' aktualisiert.");
        return "redirect:/manufacturing-bom/" + id;
    }

    // ── Delete ───────────────────────────────────────────────────
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String delete(
            @PathVariable Long id,
            RedirectAttributes ra) {

        ManufacturingBOM bom =
                mfgService.findByIdWithAllDetails(id);

        if (!bom.getProductBOMs().isEmpty()) {
            ra.addFlashAttribute("errorMessage",
                    "❌ Löschen nicht möglich: '"
                    + bom.getName() + "' ist mit "
                    + bom.getProductBOMs().size()
                    + " Product BOM(s) verknüpft.");
            return "redirect:/manufacturing-bom/" + id;
        }

        String name = bom.getName();
        mfgRepo.deleteById(id);
        ra.addFlashAttribute("successMessage",
                "🗑️ Manufacturing BOM '" + name
                + "' erfolgreich gelöscht.");
        return "redirect:/manufacturing-bom";
    }

    // ── Operation hinzufügen ─────────────────────────────────────
    @PostMapping("/{id}/operations/add")
    @PreAuthorize("hasAuthority('WRITE')")
    public String addOperation(
            @PathVariable Long id,
            @ModelAttribute ManufacturingOperation op,
            Authentication auth,
            RedirectAttributes ra) {

        op.setId(null);

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

        opRepo.save(op);
        ra.addFlashAttribute("successMessage",
                "✅ Operation '" + op.getName()
                + "' hinzugefügt.");
        return "redirect:/manufacturing-bom/" + id;
    }

    // ── Operation bearbeiten (Formular) ──────────────────────────
    @GetMapping("/{id}/operations/{opId}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editOperationForm(
            @PathVariable Long id,
            @PathVariable Long opId,
            Model model) {

        ManufacturingBOM bom = mfgRepo.findByIdOnly(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ManufacturingBOM nicht gefunden: " + id));

        ManufacturingOperation op = opRepo.findById(opId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Operation nicht gefunden: " + opId));

        model.addAttribute("mfgBOM", bom);
        model.addAttribute("op",     op);
        model.addAttribute("operationTypes",
                ManufacturingOperation.OperationType.values());
        return "manufacturing-bom/operation-edit";
    }

    // ── Operation bearbeiten (Speichern) ─────────────────────────
    @PostMapping("/{id}/operations/{opId}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editOperationSave(
            @PathVariable Long id,
            @PathVariable Long opId,
            @RequestParam String name,
            @RequestParam(required = false) String operationNumber,
            @RequestParam String operationType,
            @RequestParam(required = false) String workstation,
            @RequestParam(required = false) Integer workerCount,
            @RequestParam(required = false) Integer sequenceNumber,
            @RequestParam(required = false) BigDecimal cycleTimeTarget,
            @RequestParam(required = false) BigDecimal cycleTimeActual,
            @RequestParam(required = false) BigDecimal lineTaktTime,
            @RequestParam(required = false) BigDecimal waitTime,
            @RequestParam(required = false) String toolsRequired,
            @RequestParam(required = false) String workInstructions,
            @RequestParam(required = false) String safetyNotes,
            RedirectAttributes ra) {

        ManufacturingOperation op = opRepo.findById(opId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Operation nicht gefunden: " + opId));

        op.setName(name);
        op.setOperationNumber(operationNumber);
        op.setOperationType(
                ManufacturingOperation.OperationType
                        .valueOf(operationType));
        op.setWorkstation(workstation);
        op.setWorkerCount(workerCount != null ? workerCount : 1);
        op.setSequenceNumber(sequenceNumber);
        op.setCycleTimeTarget(cycleTimeTarget);
        op.setCycleTimeActual(cycleTimeActual);
        op.setLineTaktTime(lineTaktTime);
        op.setWaitTime(waitTime);
        op.setToolsRequired(toolsRequired);
        op.setWorkInstructions(workInstructions);
        op.setSafetyNotes(safetyNotes);

        opRepo.save(op);
        ra.addFlashAttribute("successMessage",
                "✅ Operation '" + op.getName()
                + "' erfolgreich aktualisiert.");
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

    // ── Arbeitsanweisung ─────────────────────────────────────────
    @GetMapping("/{id}/work-instruction")
    public ResponseEntity<byte[]> workInstruction(
            @PathVariable Long id) {

        String html =
                pdfService.generateWorkInstructionHtml(id);
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