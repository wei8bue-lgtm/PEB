package com.peb.controller.api;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API für Dataset-Operationen
 * Optimiert für VBScript / externe Clients
 *
 * Basis-URL: /api/v1/datasets
 */
@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
@Slf4j
public class DatasetApiController {

    private final DatasetService datasetService;

    // ══════════════════════════════════════════════════════
    // LIST - Alle Datasets einer Revision abrufen
    // GET /api/v1/datasets?revisionId=123&revisionType=DESIGN
    // GET /api/v1/datasets?revisionId=123&revisionType=EBOM&category=PDF_DOCUMENT
    // ══════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> listDatasets(
            @RequestParam Long revisionId,
            @RequestParam String revisionType,
            @RequestParam(required = false) DatasetCategory category) {
        try {
            List<Dataset> datasets = (category != null)
                    ? datasetService.getDatasetsByCategory(revisionId, revisionType, category)
                    : datasetService.getDatasets(revisionId, revisionType);

            Map<String, Object> response = new HashMap<>();
            response.put("success",  true);
            response.put("count",    datasets.size());
            response.put("datasets", datasets.stream()
                    .map(this::toSimpleMap)
                    .toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler beim Abrufen der Datasets: {}", e.getMessage());
            return ResponseEntity.badRequest().body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // GET ONE - Einzelnes Dataset abrufen
    // GET /api/v1/datasets/{datasetId}
    // ══════════════════════════════════════════════════════

    @GetMapping("/{datasetId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getDataset(@PathVariable Long datasetId) {
        try {
            Dataset dataset = datasetService.getDataset(datasetId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("dataset", toDetailMap(dataset));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Dataset nicht gefunden: {}", datasetId);
            return ResponseEntity.status(404).body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // DOWNLOAD - Datei herunterladen
    // GET /api/v1/datasets/{datasetId}/download
    // ══════════════════════════════════════════════════════

    @GetMapping("/{datasetId}/download")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> downloadDataset(@PathVariable Long datasetId) {
        try {
            Resource resource = datasetService.loadDatasetAsResource(datasetId);
            Dataset  dataset  = datasetService.getDataset(datasetId);

            String mimeType = (dataset.getMimeType() != null)
                    ? dataset.getMimeType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + dataset.getFileName() + "\"")
                    .header("X-Dataset-Id",  String.valueOf(dataset.getDatasetId()))
                    .header("X-File-Size",   String.valueOf(dataset.getFileSize()))
                    .header("X-Revision-Id", String.valueOf(dataset.getRevisionId()))
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Ungültiger Dateipfad für Dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.status(500).body(errorMap("Ungültiger Dateipfad"));
        } catch (Exception e) {
            log.error("Download-Fehler für Dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.status(404).body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // UPLOAD - Neue Datei hochladen
    // POST /api/v1/datasets/upload
    // ══════════════════════════════════════════════════════

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> uploadDataset(
            @RequestParam Long revisionId,
            @RequestParam String revisionType,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) DatasetCategory category) {
        try {
            log.info("Upload: RevisionId={}, Type={}, Datei={}, Größe={} bytes",
                    revisionId, revisionType,
                    file.getOriginalFilename(), file.getSize());

            Dataset dataset = datasetService.createDataset(
                    revisionId, revisionType, file, category);

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("message",   "Datei erfolgreich hochgeladen");
            response.put("datasetId", dataset.getDatasetId());
            response.put("fileName",  dataset.getFileName());
            response.put("fileSize",  dataset.getFileSize());
            response.put("mimeType",  dataset.getMimeType());
            response.put("category",  dataset.getCategory() != null
                                        ? dataset.getCategory().name() : null);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Validierungsfehler beim Upload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(errorMap(e.getMessage()));
        } catch (IOException e) {
            log.error("IO-Fehler beim Upload: {}", e.getMessage());
            return ResponseEntity.status(500).body(errorMap("Fehler beim Speichern"));
        }
    }

    // ══════════════════════════════════════════════════════
    // UPDATE CATEGORY
    // PATCH /api/v1/datasets/{datasetId}/category
    // ══════════════════════════════════════════════════════

    @PatchMapping("/{datasetId}/category")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> updateCategory(
            @PathVariable Long datasetId,
            @RequestParam DatasetCategory category) {
        try {
            Dataset updated = datasetService.updateDatasetCategory(datasetId, category);

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("message",   "Kategorie aktualisiert");
            response.put("datasetId", updated.getDatasetId());
            response.put("category",  updated.getCategory().name());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // DELETE - Dataset löschen
    // DELETE /api/v1/datasets/{datasetId}
    // ══════════════════════════════════════════════════════

    @DeleteMapping("/{datasetId}")
    @PreAuthorize("hasAuthority('DELETE')")
    public ResponseEntity<?> deleteDataset(@PathVariable Long datasetId) {
        try {
            Dataset dataset  = datasetService.getDataset(datasetId);
            String  fileName = dataset.getFileName();

            datasetService.deleteDataset(datasetId);

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("message",   "Dataset gelöscht: " + fileName);
            response.put("datasetId", datasetId);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Fehler beim Löschen von Dataset {}: {}", datasetId, e.getMessage());
            return ResponseEntity.status(500).body(errorMap("Fehler beim Löschen"));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // EXISTS CHECK
    // GET /api/v1/datasets/exists?revisionId=&revisionType=&fileName=
    // ══════════════════════════════════════════════════════

    @GetMapping("/exists")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> checkExists(
            @RequestParam Long   revisionId,
            @RequestParam String revisionType,
            @RequestParam String fileName) {

        boolean exists = datasetService.datasetExistsByFileName(
                revisionId, revisionType, fileName);

        Map<String, Object> response = new HashMap<>();
        response.put("success",  true);
        response.put("exists",   exists);
        response.put("fileName", fileName);

        return ResponseEntity.ok(response);
    }

    // ══════════════════════════════════════════════════════
    // CATEGORIES - Verfügbare Kategorien
    // GET /api/v1/datasets/categories
    // ══════════════════════════════════════════════════════

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getCategories() {
        List<Map<String, String>> categories = List.of(DatasetCategory.values())
                .stream()
                .map(cat -> Map.of(
                        "name",        cat.name(),
                        "displayName", cat.getDisplayName()))
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("success",    true);
        response.put("categories", categories);

        return ResponseEntity.ok(response);
    }
	
	@GetMapping("/object/{objectId}/revision/{revisionId}")
	@PreAuthorize("hasAuthority('READ')")
	public ResponseEntity<?> getDatasetsByObjectAndRevision(
        @PathVariable String objectId,
        @PathVariable Long   revisionId) {
    try {
        // RevisionType aus Präfix ableiten
        String prefix       = objectId.substring(0, 2).toUpperCase();
        String revisionType = switch (prefix) {
            case "DS" -> "DESIGN";
            case "DR" -> "DRAWING";
            default   -> "EBOM";
        };

        List<Dataset> datasets =
                datasetService.getDatasets(revisionId, revisionType);

        Map<String, Object> response = new HashMap<>();
        response.put("success",      true);
        response.put("objectId",     objectId);
        response.put("revisionId",   revisionId);
        response.put("revisionType", revisionType);
        response.put("count",        datasets.size());
        response.put("datasets",     datasets.stream()
                                             .map(this::toDetailMap)
                                             .toList());

        return ResponseEntity.ok(response);

    } catch (Exception e) {
        log.error("Fehler: {}", e.getMessage());
        return ResponseEntity.status(500).body(errorMap(e.getMessage()));
    }
}

	
@GetMapping("/object/{objectId}/latest")
@PreAuthorize("hasAuthority('READ')")
public ResponseEntity<?> getDatasetsByObjectLatest(
        @PathVariable String objectId) {
    try {
        // ✅ getDatasetsByObjectId() aus Service nutzen
        List<Dataset> datasets =
                datasetService.getDatasetsByObjectId(objectId);

        // Nur neueste RevisionId behalten
        if (!datasets.isEmpty()) {
            Long latestRevId = datasets.stream()
                    .mapToLong(Dataset::getRevisionId)
                    .max()
                    .orElse(-1L);

            datasets = datasets.stream()
                    .filter(d -> d.getRevisionId().equals(latestRevId))
                    .toList();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success",  true);
        response.put("objectId", objectId);
        response.put("count",    datasets.size());
        response.put("datasets", datasets.stream()
                                         .map(this::toDetailMap)
                                         .toList());

        return ResponseEntity.ok(response);

    } catch (Exception e) {
        log.error("Fehler: {}", e.getMessage());
        return ResponseEntity.status(500).body(errorMap(e.getMessage()));
    }
}

	
@GetMapping("/object/{objectId}/all")
@PreAuthorize("hasAuthority('READ')")
public ResponseEntity<?> getDatasetsByObject(
        @PathVariable String objectId) {
    try {
        // ✅ getDatasetsByObjectId() aus Service nutzen
        List<Dataset> datasets =
                datasetService.getDatasetsByObjectId(objectId);

        Map<String, Object> response = new HashMap<>();
        response.put("success",    true);
        response.put("objectId",   objectId);
        response.put("objectType", objectId.length() >= 2
                                    ? objectId.substring(0, 2) : "unknown");
        response.put("count",      datasets.size());
        response.put("datasets",   datasets.stream()
                                           .map(this::toDetailMap)
                                           .toList());

        return ResponseEntity.ok(response);

    } catch (IllegalArgumentException e) {
        log.warn("Ungültige Object-ID: {}", objectId);
        return ResponseEntity.badRequest().body(errorMap(e.getMessage()));
    } catch (Exception e) {
        log.error("Fehler: {}", e.getMessage());
        return ResponseEntity.status(500).body(errorMap(e.getMessage()));
    }
}



    // ══════════════════════════════════════════════════════
    // HILFSMETHODEN
    // ══════════════════════════════════════════════════════

    private Map<String, Object> toSimpleMap(Dataset d) {
        Map<String, Object> map = new HashMap<>();
        map.put("datasetId",   d.getDatasetId());
        map.put("fileName",    d.getFileName());
        map.put("fileSize",    d.getFileSize());
        map.put("mimeType",    d.getMimeType());
        map.put("category",    d.getCategory() != null
                                ? d.getCategory().name() : null);
        map.put("createdDate", d.getCreatedDate() != null
                                ? d.getCreatedDate().toString() : null);
        map.put("createdBy",   d.getCreatedBy());
        return map;
    }

    private Map<String, Object> toDetailMap(Dataset d) {
        Map<String, Object> map = toSimpleMap(d);
        map.put("revisionId",   d.getRevisionId());
        map.put("revisionType", d.getRevisionType());
        map.put("downloadUrl",  "/api/v1/datasets/" + d.getDatasetId() + "/download");
        return map;
    }

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("error",   message);
        return map;
    }
}