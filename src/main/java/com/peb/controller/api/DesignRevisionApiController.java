package com.peb.controller.api;

import com.peb.model.base.Dataset;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.service.DatasetService;
import com.peb.service.DesignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API für Design-Revisionen inkl. Datasets
 * Endpunkt: GET /api/v1/designs/{designId}/revisions
 *
 * Liefert alle Revisionen eines Designs mit den
 * jeweils angehängten Datasets - optimiert für VBScript
 */
@RestController
@RequestMapping("/api/v1/designs")
@RequiredArgsConstructor
@Slf4j
public class DesignRevisionApiController {

    private final DesignService  designService;
    private final DatasetService datasetService;

    // ══════════════════════════════════════════════════════════
    // ALLE DESIGNS ABRUFEN
    // GET /api/v1/designs
    // ══════════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAllDesigns() {
        try {
            List<Design> designs = designService.getAllDesigns();

            List<Map<String, Object>> designList = designs.stream()
                    .map(this::toDesignSummary)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count",   designList.size());
            response.put("designs", designList);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler beim Abrufen aller Designs: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap("Fehler: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // EIN DESIGN ABRUFEN
    // GET /api/v1/designs/{designId}
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{designId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getDesign(@PathVariable String designId) {
        try {
            Design design = designService.getDesign(designId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("design",  toDesignSummary(design));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.warn("Design nicht gefunden: {}", designId);
            return ResponseEntity.status(404)
                    .body(errorMap("Design nicht gefunden: " + designId));
        }
    }

    // ══════════════════════════════════════════════════════════
    // REVISIONEN MIT DATASETS ABRUFEN
    // GET /api/v1/designs/{designId}/revisions
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{designId}/revisions")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getDesignRevisions(@PathVariable String designId) {
        try {
            Design design = designService.getDesign(designId);

            List<DesignRevision> revisions = design.getRevisions();

            // Revisionen mit Datasets zusammenbauen
            List<Map<String, Object>> revisionList = new ArrayList<>();
            for (DesignRevision rev : revisions) {
                revisionList.add(toRevisionMap(rev));
            }

            // Nach Revision sortieren (01, 02, 03...)
            revisionList.sort((a, b) -> {
                String ra = (String) a.get("revision");
                String rb = (String) b.get("revision");
                return ra.compareTo(rb);
            });

            // Response zusammenbauen - exakt was VBScript erwartet
            Map<String, Object> response = new HashMap<>();
            response.put("success",       true);
            response.put("designId",      design.getId());
            response.put("designName",    design.getName());
            response.put("department",    design.getDepartment() != null
                                          ? design.getDepartment() : "");
            response.put("mass",          design.getMass() != null
                                          ? design.getMass() : "");
            response.put("material",      design.getMaterial() != null
                                          ? design.getMaterial() : "");
            response.put("linkedMaterialId", design.getLinkedMaterialId() != null
                                          ? design.getLinkedMaterialId() : "");
            response.put("revisionCount", revisionList.size());
            response.put("revisions",     revisionList);

            log.info("Revisionen abgerufen: Design={}, Anzahl={}",
                     designId, revisionList.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler bei Revisionen für Design {}: {}",
                      designId, e.getMessage());
            return ResponseEntity.status(404)
                    .body(errorMap("Design nicht gefunden: " + designId));
        }
    }

    // ══════════════════════════════════════════════════════════
    // EINE SPEZIFISCHE REVISION ABRUFEN
    // GET /api/v1/designs/{designId}/revisions/{revisionId}
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{designId}/revisions/{revisionId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getDesignRevision(
            @PathVariable String designId,
            @PathVariable Long   revisionId) {
        try {
            Design design = designService.getDesign(designId);

            DesignRevision revision = design.getRevisions().stream()
                    .filter(r -> r.getRevisionId().equals(revisionId))
                    .findFirst()
                    .orElse(null);

            if (revision == null) {
                return ResponseEntity.status(404)
                        .body(errorMap("Revision nicht gefunden: " + revisionId));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success",  true);
            response.put("designId", designId);
            response.put("revision", toRevisionMap(revision));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler bei Revision {}/{}: {}", designId, revisionId, e.getMessage());
            return ResponseEntity.status(404)
                    .body(errorMap("Nicht gefunden: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // DATASETS EINER REVISION ABRUFEN
    // GET /api/v1/designs/{designId}/revisions/{revisionId}/datasets
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{designId}/revisions/{revisionId}/datasets")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getRevisionDatasets(
            @PathVariable String designId,
            @PathVariable Long   revisionId) {
        try {
            List<Dataset> datasets = datasetService.getDatasets(revisionId, "DESIGN");

            List<Map<String, Object>> datasetList = datasets.stream()
                    .map(this::toDatasetMap)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",    true);
            response.put("designId",   designId);
            response.put("revisionId", revisionId);
            response.put("count",      datasetList.size());
            response.put("datasets",   datasetList);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler bei Datasets für {}/{}: {}",
                      designId, revisionId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap("Fehler: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // HILFSMETHODEN - Mapping
    // ══════════════════════════════════════════════════════════

    /**
     * Design → kompakte Map für Listen
     */
    private Map<String, Object> toDesignSummary(Design design) {
        Map<String, Object> map = new HashMap<>();
        map.put("designId",      design.getId());
        map.put("name",          design.getName());
        map.put("department",    design.getDepartment() != null
                                 ? design.getDepartment() : "");
        map.put("mass",          design.getMass() != null
                                 ? design.getMass() : "");
        map.put("material",          design.getMaterial() != null
                                     ? design.getMaterial() : "");
        map.put("linkedMaterialId",  design.getLinkedMaterialId() != null
                                     ? design.getLinkedMaterialId() : "");
        map.put("createdDate",       design.getCreatedDate() != null
                                 ? design.getCreatedDate().toString() : "");
        map.put("createdBy",     design.getCreatedBy() != null
                                 ? design.getCreatedBy() : "");
        map.put("revisionCount", design.getRevisions().size());

        // Letzter Status
        DesignRevision latest = design.getLatestRevision();
        if (latest != null) {
            map.put("latestRevision",      latest.getRevision());
            map.put("latestRevisionId",    latest.getRevisionId());
            map.put("latestStatus",        latest.getReleaseStatus().name());
            map.put("latestStatusDisplay", latest.getReleaseStatus().getDisplayName());
        }

        return map;
    }

    /**
     * DesignRevision → detaillierte Map inkl. Datasets
     * Exakt die Felder die das VBScript erwartet!
     */
    private Map<String, Object> toRevisionMap(DesignRevision rev) {
        Map<String, Object> map = new HashMap<>();

        // Basis-Felder
        map.put("revisionId",    rev.getRevisionId());
        map.put("revision",      rev.getRevision());
        map.put("name",          rev.getName() != null
                                 ? rev.getName() : "");
        map.put("department",    rev.getDepartment() != null
                                 ? rev.getDepartment() : "");
        map.put("mass",          rev.getMass() != null
                                 ? rev.getMass() : "");
        map.put("material",      rev.getMaterial() != null
                                 ? rev.getMaterial() : "");

        // Status
        map.put("releaseStatus", rev.getReleaseStatus().name());
        map.put("statusDisplay", rev.getReleaseStatus().getDisplayName());
        map.put("statusIcon",    rev.getReleaseStatus().getIcon());

        // Audit
        map.put("createdDate",   rev.getCreatedDate() != null
                                 ? rev.getCreatedDate().toString() : "");
        map.put("createdBy",     rev.getCreatedBy() != null
                                 ? rev.getCreatedBy() : "");
        map.put("modifiedDate",  rev.getLastModifiedDate() != null
                                 ? rev.getLastModifiedDate().toString() : "");
        map.put("modifiedBy",    rev.getLastModifiedBy() != null
                                 ? rev.getLastModifiedBy() : "");

        // JT-Datei falls vorhanden
        map.put("jtFilePath",    rev.getJtFilePath() != null
                                 ? rev.getJtFilePath() : "");

        // Checkout-Status
        map.put("checkedOut",    rev.isCheckedOut());
        map.put("checkedOutBy",  rev.getCheckedOutBy() != null
                                 ? rev.getCheckedOutBy() : "");

        // Datasets laden und einbetten
        List<Dataset> datasets = datasetService.getDatasets(
                rev.getRevisionId(), "DESIGN");

        List<Map<String, Object>> datasetList = datasets.stream()
                .map(this::toDatasetMap)
                .toList();

        map.put("datasetCount", datasetList.size());
        map.put("datasets",     datasetList);

        return map;
    }

    /**
     * Dataset → Map
     * Enthält downloadUrl für direkten VBScript-Zugriff
     */
    private Map<String, Object> toDatasetMap(Dataset dataset) {
        Map<String, Object> map = new HashMap<>();
        map.put("datasetId",   dataset.getDatasetId());
        map.put("fileName",    dataset.getFileName() != null
                               ? dataset.getFileName() : "");
        map.put("fileSize",    dataset.getFileSize() != null
                               ? dataset.getFileSize() : 0L);
        map.put("mimeType",    dataset.getMimeType() != null
                               ? dataset.getMimeType() : "");
        map.put("category",    dataset.getCategory() != null
                               ? dataset.getCategory().getDisplayName() : "");
        map.put("categoryKey", dataset.getCategory() != null
                               ? dataset.getCategory().name() : "");
        map.put("createdDate", dataset.getCreatedDate() != null
                               ? dataset.getCreatedDate().toString() : "");
        map.put("createdBy",   dataset.getCreatedBy() != null
                               ? dataset.getCreatedBy() : "");

        // Download-URL direkt einbetten → VBScript kann sofort herunterladen
        map.put("downloadUrl",
                "/api/v1/datasets/" + dataset.getDatasetId() + "/download");

        return map;
    }

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("error",   message);
        return map;
    }
}