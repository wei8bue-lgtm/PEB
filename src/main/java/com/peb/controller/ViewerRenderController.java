package com.peb.controller.api;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import com.peb.service.JtConverterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * API-Endpunkte für den 3D-Viewer-Renderer.
 *
 * GET /api/v1/viewer/{datasetId}/glb
 *     → Liefert konvertierte GLB-Datei für Three.js
 *
 * GET /api/v1/viewer/{objectId}/revision/{revisionId}/rendering
 *     → Liefert alle DESIGN_RENDERING Datasets einer Revision
 */
@RestController
@RequestMapping("/api/v1/viewer")
@RequiredArgsConstructor
@Slf4j
public class ViewerRenderController {

    private final DatasetService    datasetService;
    private final JtConverterService jtConverterService;

    /**
     * GLB-Datei für einen Dataset ausliefern.
     * Three.js lädt diese URL direkt.
     */
    @GetMapping("/{datasetId}/glb")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Resource> getGlb(
            @PathVariable Long datasetId) {
        try {
            Dataset dataset = datasetService.getDataset(datasetId);

            // Nur viewer-kompatible Kategorien erlauben
            if (dataset.getCategory() == null
                    || !dataset.getCategory().isViewerCompatible()) {
                return ResponseEntity.status(403).build();
            }

            Path    glbPath  = jtConverterService.getOrConvertToGlb(datasetId);
            Resource resource = new PathResource(glbPath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "model/gltf-binary"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + datasetId + ".glb\"")
                    .header("Cache-Control",
                            "max-age=3600")
                    .body(resource);

        } catch (Exception e) {
            log.error("GLB-Auslieferung fehlgeschlagen: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }
	
			/**
		 * Konverter-Status abrufen
		 * GET /api/v1/viewer/converter-status
		 */
		@GetMapping("/converter-status")
		@PreAuthorize("hasAuthority('READ')")
		public ResponseEntity<?> getConverterStatus() {

			JtConverterService.ConverterInfo info =
					jtConverterService.getConverterInfo();

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("success",           true);
			response.put("converterConfigured", info.configured());
			response.put("converterAvailable",  info.available());
			response.put("converterPath",       info.path());
			response.put("supportedFormats",
						 List.of("GLB", "GLTF", "JT"));
			response.put("jtSupport",
						 info.available() ? "Extern (CLI)" : "Fallback-GLB");
			response.put("cacheEnabled", true);

			return ResponseEntity.ok(response);
		}

		/**
		 * Cache für ein Dataset invalidieren
		 * DELETE /api/v1/viewer/{datasetId}/glb/cache
		 */
		@DeleteMapping("/{datasetId}/glb/cache")
		@PreAuthorize("hasAuthority('WRITE')")
		public ResponseEntity<?> invalidateGlbCache(
				@PathVariable Long datasetId) {
			try {
				jtConverterService.invalidateCache(datasetId);
				return ResponseEntity.ok(Map.of(
						"success",   true,
						"message",   "Cache invalidiert für Dataset: " + datasetId,
						"datasetId", datasetId));
			} catch (Exception e) {
				return ResponseEntity.status(500).body(Map.of(
						"success", false,
						"error",   e.getMessage()));
			}
		}

    /**
     * Alle Rendering-Datasets einer Revision abrufen.
     * Sortiert nach DatasetCategory.ordinal().
     */
    @GetMapping("/{objectId}/revision/{revisionId}/rendering")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getRenderingDatasets(
            @PathVariable String objectId,
            @PathVariable Long   revisionId) {
        try {
            String prefix       = objectId.substring(0, 2).toUpperCase();
            String revisionType = switch (prefix) {
                case "DS" -> "DESIGN";
                case "DR" -> "DRAWING";
                default   -> "EBOM";
            };

            List<Dataset> all = datasetService
                    .getDatasets(revisionId, revisionType);

            // Viewer-kompatible Datasets, sortiert nach ordinal()
            List<Map<String, Object>> renderingDatasets = all.stream()
                    .filter(d -> d.getCategory() != null
                                 && d.getCategory().isViewerCompatible())
                    .sorted(Comparator.comparingInt(
                            d -> d.getCategory().ordinal()))
                    .map(d -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("datasetId",    d.getDatasetId());
                        m.put("fileName",     d.getFileName());
                        m.put("category",     d.getCategory().name());
                        m.put("categoryDisplay",
                              d.getCategory().getDisplayName());
                        m.put("glbUrl",
                              "/api/v1/viewer/" + d.getDatasetId() + "/glb");
                        m.put("downloadUrl",
                              "/api/v1/datasets/" + d.getDatasetId()
                              + "/download");
                        return m;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success",           true);
            response.put("objectId",          objectId);
            response.put("revisionId",        revisionId);
            response.put("count",             renderingDatasets.size());
            response.put("renderingDatasets", renderingDatasets);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Rendering-Datasets Fehler: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("success", false,
                                 "error",   e.getMessage()));
        }
    }
}