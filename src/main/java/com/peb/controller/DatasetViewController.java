package com.peb.controller;

import com.peb.exception.ObjectNotFoundException;
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
 * Controller für Dataset-Verwaltungsseiten (Thymeleaf Views)
 */
@Controller
@RequestMapping("/datasets")
@RequiredArgsConstructor
@Slf4j
public class DatasetViewController {

    private final DatasetService datasetService;

    // ══════════════════════════════════════════════════
    // DATASET SEITE ANZEIGEN
    // ══════════════════════════════════════════════════

    @GetMapping("/manage/{revisionType}/{revisionId}")
    public String manageDatasets(
            @PathVariable String revisionType,
            @PathVariable Long   revisionId,
            Model model) {

        try {
            List<Dataset> datasets = datasetService
                    .getDatasets(revisionId, revisionType);

            // Statistiken im Controller berechnen
            long totalBytes = 0L;
            long countWithCategory = 0L;

            for (Dataset d : datasets) {
                if (d.getFileSize() != null) {
                    totalBytes += d.getFileSize();
                }
                if (d.getCategory() != null) {
                    countWithCategory++;
                }
            }

            // Größe formatieren
            String totalSizeFormatted;
            if (totalBytes >= 1048576) {
                totalSizeFormatted = String.format("%.1f MB",
                        totalBytes / 1048576.0);
            } else if (totalBytes >= 1024) {
                totalSizeFormatted = String.format("%.1f KB",
                        totalBytes / 1024.0);
            } else {
                totalSizeFormatted = totalBytes + " Bytes";
            }

            // Seitentitel
            String pageTitle = switch (revisionType.toUpperCase()) {
                case "DESIGN"  -> "Datasets - Design Revision";
                case "EBOM"    -> "Datasets - eBOM Revision";
                case "DRAWING" -> "Datasets - Drawing Revision";
                default        -> "Datasets - Revision " + revisionId;
            };

            model.addAttribute("datasets",          datasets);
            model.addAttribute("revisionId",        revisionId);
            model.addAttribute("revisionType",      revisionType);
            model.addAttribute("categories",        DatasetCategory.values());
            model.addAttribute("pageTitle",         pageTitle);
            model.addAttribute("totalSize",         totalSizeFormatted);
            model.addAttribute("countWithCategory", countWithCategory);
            model.addAttribute("datasetCount",      datasets.size());

            log.info("Dataset-Seite: type={}, revisionId={}, count={}",
                     revisionType, revisionId, datasets.size());

        } catch (Exception e) {
            log.error("Fehler beim Laden der Datasets: {}", e.getMessage());
            model.addAttribute("error",             "Fehler: " + e.getMessage());
            model.addAttribute("datasets",          List.of());
            model.addAttribute("revisionId",        revisionId);
            model.addAttribute("revisionType",      revisionType);
            model.addAttribute("categories",        DatasetCategory.values());
            model.addAttribute("pageTitle",         "Datasets");
            model.addAttribute("totalSize",         "0 Bytes");
            model.addAttribute("countWithCategory", 0L);
            model.addAttribute("datasetCount",      0);
        }

        return "dataset-manage";
    }

    // ══════════════════════════════════════════════════
    // UPLOAD
    // ══════════════════════════════════════════════════

    @PostMapping("/upload")
    public String uploadDataset(
            @RequestParam Long                          revisionId,
            @RequestParam String                        revisionType,
            @RequestParam MultipartFile                 file,
            @RequestParam(required = false)
                          DatasetCategory               category,
            RedirectAttributes                          redirectAttributes) {

        String redirectUrl = "redirect:/datasets/manage/"
                + revisionType + "/" + revisionId;

        try {
            if (file == null || file.isEmpty()) {
                redirectAttributes.addFlashAttribute(
                        "error", "Bitte eine Datei auswählen!");
                return redirectUrl;
            }

            Dataset dataset = datasetService.createDataset(
                    revisionId, revisionType, file, category);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Datei '" + dataset.getFileName()
                    + "' erfolgreich hochgeladen!");

            log.info("Upload OK: {}, RevisionId={}",
                     dataset.getFileName(), revisionId);

        } catch (IllegalArgumentException e) {
            log.warn("Upload Validierung: {}", e.getMessage());
            redirectAttributes.addFlashAttribute(
                    "error", "Validierungsfehler: " + e.getMessage());

        } catch (IOException e) {
            log.error("Upload IO-Fehler: {}", e.getMessage());
            redirectAttributes.addFlashAttribute(
                    "error", "Upload fehlgeschlagen: " + e.getMessage());

        } catch (Exception e) {
            log.error("Upload unbekannter Fehler: {}", e.getMessage());
            redirectAttributes.addFlashAttribute(
                    "error", "Unbekannter Fehler: " + e.getMessage());
        }

        return redirectUrl;
    }

    // ══════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════

    @PostMapping("/delete/{datasetId}")
    public String deleteDataset(
            @PathVariable Long         datasetId,
            @RequestParam Long         revisionId,
            @RequestParam String       revisionType,
            RedirectAttributes         redirectAttributes) {

        String redirectUrl = "redirect:/datasets/manage/"
                + revisionType + "/" + revisionId;

        try {
            // ── Dateiname VOR dem Löschen holen ──────────────
            // Direkt aus der Liste der Revision holen
            // um getDataset() zu vermeiden falls nicht vorhanden
            List<Dataset> datasets = datasetService
                    .getDatasets(revisionId, revisionType);

            String fileName = datasets.stream()
                    .filter(d -> d.getDatasetId().equals(datasetId))
                    .map(Dataset::getFileName)
                    .findFirst()
                    .orElse("ID: " + datasetId);

            datasetService.deleteDataset(datasetId);

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Datei '" + fileName + "' erfolgreich gelöscht!");

            log.info("Dataset gelöscht: ID={}", datasetId);

        } catch (IOException e) {
            log.error("Lösch IO-Fehler: {}", e.getMessage());
            redirectAttributes.addFlashAttribute(
                    "error", "Löschen fehlgeschlagen: " + e.getMessage());

        } catch (ObjectNotFoundException e) {
            log.warn("Dataset nicht gefunden: ID={}", datasetId);
            redirectAttributes.addFlashAttribute(
                    "error", "Dataset nicht gefunden: " + datasetId);

        } catch (Exception e) {
            log.error("Lösch-Fehler: {}", e.getMessage());
            redirectAttributes.addFlashAttribute(
                    "error", "Fehler beim Löschen: " + e.getMessage());
        }

        return redirectUrl;
    }

    // ══════════════════════════════════════════════════
    // DOWNLOAD REDIRECT
    // ══════════════════════════════════════════════════

    @GetMapping("/download/{datasetId}")
    public String downloadRedirect(@PathVariable Long datasetId) {
        return "redirect:/api/v1/datasets/" + datasetId + "/download";
    }
}