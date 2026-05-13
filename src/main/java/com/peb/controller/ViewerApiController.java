package com.peb.controller.api;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/viewer")
@RequiredArgsConstructor
@Slf4j
public class ViewerApiController {

    private final DatasetService datasetService;

    @GetMapping("/{objectId}/revision/{revisionId}/assemblies")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAssemblies(
            @PathVariable String objectId,
            @PathVariable Long   revisionId) {
        try {
            String        revisionType = resolveRevisionType(objectId);
            List<Dataset> datasets     = datasetService.getDatasets(revisionId, revisionType);

            List<Map<String, Object>> assemblies = datasets.stream()
                    .sorted(Comparator.comparingInt(
                            d -> d.getCategory() != null
                                 ? d.getCategory().ordinal()
                                 : Integer.MAX_VALUE))
                    .map(d -> toAssemblyMap(d, objectId, revisionId))
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success",          true);
            response.put("objectId",         objectId);
            response.put("revisionId",       revisionId);
            response.put("count",            assemblies.size());
            response.put("assemblies",       assemblies);
            response.put("sortedBy",         "DatasetCategory.ordinal()");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler Baugruppen abrufen: {}", e.getMessage());
            return ResponseEntity.status(500).body(errorMap(e.getMessage()));
        }
    }

    /**
     * Kategorie-Reihenfolge und Viewer-Kompatibilität aus Enum
     * GET /api/v1/viewer/category-order
     */
    @GetMapping("/category-order")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getCategoryOrder() {

        List<Map<String, Object>> order = Arrays.stream(DatasetCategory.values())
                .map(cat -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ordinal",          cat.ordinal());
                    m.put("name",             cat.name());
                    m.put("displayName",      cat.getDisplayName());
                    m.put("viewerCompatible", cat.isViewerCompatible());
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success",       true);
        response.put("categoryOrder", order);
        response.put("source",        "DatasetCategory.java – Enum-Reihenfolge");

        return ResponseEntity.ok(response);
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────

    private Map<String, Object> toAssemblyMap(Dataset d,
                                               String objectId,
                                               Long revisionId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("datasetId",       d.getDatasetId());
        map.put("fileName",        d.getFileName()  != null ? d.getFileName()  : "");
        map.put("fileSize",        d.getFileSize()  != null ? d.getFileSize()  : 0L);
        map.put("mimeType",        d.getMimeType()  != null ? d.getMimeType()  : "");
        map.put("downloadUrl",     "/api/v1/datasets/" + d.getDatasetId() + "/download");
        map.put("createdDate",     d.getCreatedDate() != null
                                   ? d.getCreatedDate().toString() : "");
        map.put("createdBy",       d.getCreatedBy() != null ? d.getCreatedBy() : "");

        if (d.getCategory() != null) {
            map.put("categoryOrdinal",  d.getCategory().ordinal());
            map.put("categoryName",     d.getCategory().name());
            map.put("categoryDisplay",  d.getCategory().getDisplayName());
            map.put("viewerCompatible", d.getCategory().isViewerCompatible());
        } else {
            map.put("categoryOrdinal",  Integer.MAX_VALUE);
            map.put("categoryName",     null);
            map.put("categoryDisplay",  "Keine Kategorie");
            map.put("viewerCompatible", false);
        }

        return map;
    }

    private String resolveRevisionType(String objectId) {
        if (objectId == null || objectId.length() < 2) return "DESIGN";
        return switch (objectId.substring(0, 2).toUpperCase()) {
            case "DS" -> "DESIGN";
            case "DR" -> "DRAWING";
            default   -> "EBOM";
        };
    }

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("error",   message);
        return map;
    }
}