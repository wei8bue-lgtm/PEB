package com.peb.controller;

import com.peb.dto.DesignDTO;
import com.peb.model.base.Dataset;
import com.peb.model.cad.Design;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;   // ← NEU
import com.peb.service.DesignService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/designs")
@RequiredArgsConstructor
public class DesignController {

    private final DesignService  designService;
    private final DatasetService datasetService;  // ← NEU injizieren

    @GetMapping
    public String listDesigns(Model model) {
        model.addAttribute("designs", designService.getAllDesigns());
        return "designs/design-list";
    }

    @GetMapping("/new")
    public String newDesignForm(Model model) {
        model.addAttribute("design", new DesignDTO());
        return "designs/design-form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE')")
    public String createDesign(@ModelAttribute DesignDTO designDTO, Model model) {
        try {
            Design design = designService.createDesign(
                    designDTO.getName(),
                    designDTO.getDepartment());
            return "redirect:/designs/" + design.getId();
        } catch (Exception e) {
            model.addAttribute("error",
                    "Failed to create design: " + e.getMessage());
            model.addAttribute("design", designDTO);
            return "designs/design-form";
        }
    }

@GetMapping("/{id}")
public String viewDesign(@PathVariable String id, Model model) {

    Design design = designService.getDesign(id);
    model.addAttribute("design", design);

    if (design.getLatestRevision() != null) {

        Long   revisionId   = design.getLatestRevision().getRevisionId();
        String revisionType = "DESIGN";

        List<Dataset> allDatasets =
                datasetService.getDatasets(revisionId, revisionType);

        // ── PDF Dataset (PDF_DOCUMENT > PDF_DRAWING) ───────────────
        Dataset pdfDataset = allDatasets.stream()
                .filter(ds -> ds.getCategory() == DatasetCategory.PDF_DOCUMENT
                           || ds.getCategory() == DatasetCategory.PDF_DRAWING)
                .min(java.util.Comparator.comparingInt(
                        ds -> ds.getCategory().ordinal()))
                .orElse(null);

        // ── Excel Dataset ──────────────────────────────────────────
        Dataset excelDataset = allDatasets.stream()
                .filter(ds -> ds.getCategory() == DatasetCategory.EXCEL_TABLE)
                .findFirst()
                .orElse(null);

        model.addAttribute("pdfDataset",   pdfDataset);
        model.addAttribute("excelDataset", excelDataset);

        // ── Rückwärtskompatibilität: embeddedDataset bleibt ────────
        // Priorität: PDF > Excel
        Dataset embeddedDataset = pdfDataset != null
                ? pdfDataset : excelDataset;
        model.addAttribute("embeddedDataset",  embeddedDataset);
        model.addAttribute("embeddedCategory",
                embeddedDataset != null
                        ? embeddedDataset.getCategory() : null);

    } else {
        model.addAttribute("pdfDataset",   null);
        model.addAttribute("excelDataset", null);
        model.addAttribute("embeddedDataset",  null);
        model.addAttribute("embeddedCategory", null);
    }

    return "designs/design-view";
}

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editDesignForm(@PathVariable String id, Model model) {
        Design design = designService.getDesign(id);

        DesignDTO dto = new DesignDTO();
        dto.setName(design.getName());
        dto.setDepartment(design.getDepartment());
        dto.setMass(design.getMass());
        dto.setMaterial(design.getMaterial());
        dto.setChildView(design.getChildView());

        model.addAttribute("design",    dto);
        model.addAttribute("designId",  id);
        return "designs/design-edit";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String updateDesign(@PathVariable String id,
                               @ModelAttribute DesignDTO designDTO) {
        designService.updateDesign(
                id,
                designDTO.getName(),
                designDTO.getDepartment(),
                designDTO.getMass(),
                designDTO.getMaterial(),
                designDTO.getChildView());
        return "redirect:/designs/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String deleteDesign(@PathVariable String id) {
        designService.deleteDesign(id);
        return "redirect:/designs";
    }
}