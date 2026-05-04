package com.peb.controller;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

/**
 * Controller für Dataset-Verwaltungsseiten
 */
@Controller
@RequestMapping("/datasets")
@RequiredArgsConstructor
@Slf4j
public class DatasetViewController {

    private final DatasetService datasetService;

    @GetMapping("/manage/{revisionType}/{revisionId}")
    public String manageDatasets(
            @PathVariable String revisionType,
            @PathVariable Long   revisionId,
            Model model) {

        try {
            List<Dataset> datasets = datasetService.getDatasets(revisionId, revisionType);

            // ── Statistiken im Controller berechnen ──────────────
            long totalSize     = 0;
            long withCategory  = 0;

            for (Dataset d : datasets) {
                if (d.getFileSize() != null) {
                    totalSize += d.getFileSize();
                }
                if (d.getCategory() != null) {
                    withCategory++;
                }
            }

            // Größe formatieren
            String totalSizeFormatted;
            if (totalSize >= 1048576) {
                totalSizeFormatted = String.format("%.1f MB", totalSize / 1048576.0);
            } else if (totalSize >= 1024) {
                totalSizeFormatted = String.format("%.1f KB", totalSize / 1024.0);
            } else {
                totalSizeFormatted = totalSize + " Bytes";
            }

            // Model befüllen
            model.addAttribute("datasets",            datasets);
            model.addAttribute("revisionId",          revisionId);
            model.addAttribute("revisionType",        revisionType);
            model.addAttribute("categories",          DatasetCategory.values());
            model.addAttribute("totalCount",          datasets.size());
            model.addAttribute("totalSizeFormatted",  totalSizeFormatted);
            model.addAttribute("withCategoryCount",   withCategory);

            // Seitentitel
            String pageTitle = switch (revisionType.toUpperCase()) {
                case "DESIGN"  -> "Datasets - Design Revision";
                case "EBOM"    -> "Datasets - eBOM Revision";
                case "DRAWING" -> "Datasets - Drawing Revision";
                default        -> "Datasets - Revision " + revisionId;
            };
            model.addAttribute("pageTitle", pageTitle);

        } catch (Exception e) {
            log.error("Fehler beim Laden der Datasets: {}", e.getMessage(), e);
            model.addAttribute("error",           "Fehler beim Laden: " + e.getMessage());
            model.addAttribute("datasets",         List.of());
            model.addAttribute("revisionId",       revisionId);
            model.addAttribute("revisionType",     revisionType);
            model.addAttribute("categories",       DatasetCategory.values());
            model.addAttribute("totalCount",       0);
            model.addAttribute("totalSizeFormatted", "0 Bytes");
            model.addAttribute("withCategoryCount",  0);
            model.addAttribute("pageTitle",        "Datasets");
        }

        return "dataset-manage";
    }

    @PostMapping("/upload")
    public String uploadDataset(
            @RequestParam Long                          revisionId,
            @RequestParam String                        revisionType,
            @RequestParam MultipartFile                 file,
            @RequestParam(required = false) DatasetCategory category,
            RedirectAttributes                          redirectAttributes) {

        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error",
                        "Bitte eine Datei auswählen!");
                return "redirect:/datasets/manage/" + revisionType + "/" + revisionId;
            }

            Dataset dataset = datasetService.createDataset(
                    revisionId, revisionType, file, category);

            redirectAttributes.addFlashAttribute("success",
                    "Datei '" + dataset.getFileName() + "' erfolgreich hochgeladen!");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Validierungsfehler: " + e.getMessage());
        } catch (IOException e) {
            log.error("Upload-Fehler: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Upload fehlgeschlagen: " + e.getMessage());
        }

        return "redirect:/datasets/manage/" + revisionType + "/" + revisionId;
    }

    @GetMapping("/download/{datasetId}")
    public String downloadDataset(@PathVariable Long datasetId) {
        return "redirect:/api/v1/datasets/" + datasetId + "/download";
    }

    @PostMapping("/delete/{datasetId}")
    public String deleteDataset(
            @PathVariable Long         datasetId,
            @RequestParam Long         revisionId,
            @RequestParam String       revisionType,
            RedirectAttributes         redirectAttributes) {

        try {
            Dataset dataset  = datasetService.getDataset(datasetId);
            String  fileName = dataset.getFileName();

            datasetService.deleteDataset(datasetId);

            redirectAttributes.addFlashAttribute("success",
                    "Datei '" + fileName + "' erfolgreich gelöscht!");

        } catch (IOException e) {
            log.error("Lösch-Fehler: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error",
                    "Löschen fehlgeschlagen: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Fehler: " + e.getMessage());
        }

        return "redirect:/datasets/manage/" + revisionType + "/" + revisionId;
    }
}