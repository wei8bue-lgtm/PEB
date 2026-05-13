package com.peb.controller;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import com.peb.service.DesignService;
import com.peb.service.EBOMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/viewer")
@RequiredArgsConstructor
@Slf4j
public class ViewerController {

    private final DatasetService datasetService;
    private final DesignService  designService;
    private final EBOMService    ebomService;

    @GetMapping("/{objectId}/revision/{revisionId}")
    @PreAuthorize("hasAuthority('READ')")
    public String viewerForRevision(
            @PathVariable String objectId,
            @PathVariable Long   revisionId,
            @RequestParam(required = false) DatasetCategory filterCategory,
            Model model) {

        String        revisionType = resolveRevisionType(objectId);
        List<Dataset> allDatasets  = datasetService.getDatasets(revisionId, revisionType);

        // Sortierung nach DatasetCategory.ordinal() – keine separate Konfiguration
        List<Dataset> sortedDatasets = allDatasets.stream()
                .filter(d -> d.getCategory() != null)
                .sorted(Comparator.comparingInt(d -> d.getCategory().ordinal()))
                .collect(Collectors.toList());

        // Datasets ohne Kategorie ans Ende
        allDatasets.stream()
                .filter(d -> d.getCategory() == null)
                .forEach(sortedDatasets::add);

        // Optionaler Kategorie-Filter
        List<Dataset> displayedDatasets = (filterCategory != null)
                ? sortedDatasets.stream()
                        .filter(d -> filterCategory.equals(d.getCategory()))
                        .collect(Collectors.toList())
                : sortedDatasets;

        // Viewer-Datasets: Flag direkt aus Enum
        List<Dataset> viewerDatasets = sortedDatasets.stream()
                .filter(d -> d.getCategory() != null
                             && d.getCategory().isViewerCompatible())
                .collect(Collectors.toList());

        // Verfügbare Kategorien für Filter (nur vorhandene, Enum-Reihenfolge)
        List<DatasetCategory> availableCategories = Arrays.stream(DatasetCategory.values())
                .filter(cat -> allDatasets.stream()
                        .anyMatch(d -> cat.equals(d.getCategory())))
                .collect(Collectors.toList());

        model.addAttribute("objectId",            objectId);
        model.addAttribute("objectName",          resolveObjectName(objectId));
        model.addAttribute("revisionId",          revisionId);
        model.addAttribute("revisionType",        revisionType);
        model.addAttribute("datasets",            displayedDatasets);
        model.addAttribute("viewerDatasets",      viewerDatasets);
        model.addAttribute("allCategories",       DatasetCategory.values());
        model.addAttribute("availableCategories", availableCategories);
        model.addAttribute("filterCategory",      filterCategory);
        model.addAttribute("totalCount",          allDatasets.size());
        model.addAttribute("viewerCount",         viewerDatasets.size());

        log.info("Viewer: objectId={}, revisionId={}, total={}, viewer={}",
                objectId, revisionId, displayedDatasets.size(), viewerDatasets.size());

        return "viewer/viewer";
    }

    @GetMapping("/{objectId}")
    @PreAuthorize("hasAuthority('READ')")
    public String viewerForObject(@PathVariable String objectId, Model model) {
        Long latestRevisionId = resolveLatestRevisionId(objectId);

        if (latestRevisionId == null) {
            model.addAttribute("error",
                    "Keine Revision gefunden für Objekt: " + objectId);
            model.addAttribute("objectId", objectId);
            return "viewer/viewer-error";
        }

        return "redirect:/viewer/" + objectId + "/revision/" + latestRevisionId;
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────

    private String resolveRevisionType(String objectId) {
        if (objectId == null || objectId.length() < 2) return "DESIGN";
        return switch (objectId.substring(0, 2).toUpperCase()) {
            case "DS" -> "DESIGN";
            case "DR" -> "DRAWING";
            default   -> "EBOM";
        };
    }

    private String resolveObjectName(String objectId) {
        if (objectId == null || objectId.length() < 2) return objectId;
        try {
            return switch (objectId.substring(0, 2).toUpperCase()) {
                case "DS" -> designService.getDesign(objectId).getName();
                case "DR" -> "Drawing " + objectId;
                default   -> ebomService.getEBOMObject(objectId).getName();
            };
        } catch (Exception e) {
            log.warn("Objektname nicht gefunden für {}: {}", objectId, e.getMessage());
            return objectId;
        }
    }

    private Long resolveLatestRevisionId(String objectId) {
        if (objectId == null || objectId.length() < 2) return null;
        try {
            return switch (objectId.substring(0, 2).toUpperCase()) {
                case "DS" -> {
                    var rev = designService.getDesign(objectId).getLatestRevision();
                    yield rev != null ? rev.getRevisionId() : null;
                }
                default -> {
                    var rev = ebomService.getEBOMObject(objectId).getLatestRevision();
                    yield rev != null ? rev.getRevisionId() : null;
                }
            };
        } catch (Exception e) {
            log.warn("Revision nicht gefunden für {}: {}", objectId, e.getMessage());
            return null;
        }
    }
}