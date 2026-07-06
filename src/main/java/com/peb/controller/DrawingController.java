package com.peb.controller;

import com.peb.dto.DrawingDTO;
import com.peb.model.base.Dataset;
import com.peb.model.cad.Drawing;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import com.peb.service.DrawingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

/**
 * Controller for Drawing operations
 */
@Controller
@RequestMapping("/drawings")
@RequiredArgsConstructor
public class DrawingController {

    private final DrawingService drawingService;
    private final DatasetService datasetService;

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    public String listDrawings(Model model) {
        List<Drawing> drawings = drawingService.getAllDrawings();
        model.addAttribute("drawings", drawings);
        return "drawing-list";
    }

    // ── New Form ──────────────────────────────────────────────────────────────

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('WRITE')")
    public String newDrawingForm(Model model) {
        model.addAttribute("drawing", new DrawingDTO());
        return "drawing-form";
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE')")
    public String createDrawing(@ModelAttribute DrawingDTO drawingDTO, Model model) {
        try {
            Drawing drawing = drawingService.createDrawing(
                    drawingDTO.getName(),
                    drawingDTO.getDepartment()
            );
            return "redirect:/drawings/" + drawing.getId();
        } catch (Exception e) {
            model.addAttribute("error", "Failed to create drawing: " + e.getMessage());
            model.addAttribute("drawing", drawingDTO);
            return "drawing-form";
        }
    }

    // ── View ──────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String viewDrawing(@PathVariable String id, Model model) {

        Drawing drawing = drawingService.getDrawing(id);
        model.addAttribute("drawing", drawing);

        if (drawing.getLatestRevision() != null) {

            Long   revisionId   = drawing.getLatestRevision().getRevisionId();
            String revisionType = "DRAWING";

            List<Dataset> allDatasets =
                    datasetService.getDatasets(revisionId, revisionType);

            // PDF Dataset – PDF_DOCUMENT hat Vorrang vor PDF_DRAWING
            Dataset pdfDataset = allDatasets.stream()
                    .filter(ds -> ds.getCategory() == DatasetCategory.PDF_DOCUMENT
                               || ds.getCategory() == DatasetCategory.PDF_DRAWING)
                    .min(Comparator.comparingInt(ds -> ds.getCategory().ordinal()))
                    .orElse(null);

            // Excel Dataset
            Dataset excelDataset = allDatasets.stream()
                    .filter(ds -> ds.getCategory() == DatasetCategory.EXCEL_TABLE)
                    .findFirst()
                    .orElse(null);

            model.addAttribute("pdfDataset",   pdfDataset);
            model.addAttribute("excelDataset", excelDataset);

        } else {
            model.addAttribute("pdfDataset",   null);
            model.addAttribute("excelDataset", null);
        }

        return "drawing-view";
    }

    // ── Edit Form ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('WRITE')")
    public String editDrawingForm(@PathVariable String id, Model model) {
        Drawing drawing = drawingService.getDrawing(id);
        DrawingDTO dto = new DrawingDTO();
        dto.setName(drawing.getName());
        dto.setDepartment(drawing.getDepartment());
        model.addAttribute("drawing", dto);
        model.addAttribute("drawingId", id);
        return "drawing-edit";
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public String updateDrawing(@PathVariable String id,
                                @ModelAttribute DrawingDTO drawingDTO) {
        drawingService.updateDrawing(
                id,
                drawingDTO.getName(),
                drawingDTO.getDepartment());
        return "redirect:/drawings/" + id;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('DELETE')")
    public String deleteDrawing(@PathVariable String id) {
        drawingService.deleteDrawing(id);
        return "redirect:/drawings";
    }
}