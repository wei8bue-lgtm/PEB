package com.peb.controller.api;

import com.peb.dto.MaterialDTO;
import com.peb.exception.ObjectNotFoundException;
import com.peb.service.MaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API für Materialstammdaten.
 *
 * GET /api/v1/materials              → alle Materialien
 * GET /api/v1/materials?filter=released → nur freigegebene (RELEASED-Revision + Status ACTIVE)
 * GET /api/v1/materials/{materialId} → einzelnes Material
 */
@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
@Slf4j
public class MaterialApiController {

    private final MaterialService materialService;

    // ══════════════════════════════════════════════════════════
    // ALLE MATERIALIEN / GEFILTERT ABRUFEN
    // GET /api/v1/materials
    // GET /api/v1/materials?filter=released
    // ══════════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getMaterials(
            @RequestParam(name = "filter", required = false) String filter) {
        try {
            List<MaterialDTO> materials;

            if ("released".equalsIgnoreCase(filter)) {
                materials = materialService.getReleasedActiveMaterials();
                log.info("API: {} freigegebene Materialien abgerufen", materials.size());
            } else {
                materials = materialService.findAll();
                log.info("API: {} Materialien abgerufen", materials.size());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("count",     materials.size());
            response.put("filter",    filter != null ? filter : "none");
            response.put("materials", materials);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler beim Abrufen der Materialien: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap("Fehler: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // EIN MATERIAL ABRUFEN
    // GET /api/v1/materials/{materialId}
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{materialId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getMaterial(@PathVariable String materialId) {
        try {
            MaterialDTO material = materialService.getMaterial(materialId);

            Map<String, Object> response = new HashMap<>();
            response.put("success",  true);
            response.put("material", material);

            log.info("API: Material {} abgerufen", materialId);
            return ResponseEntity.ok(response);

        } catch (ObjectNotFoundException e) {
            log.warn("API: Material nicht gefunden: {}", materialId);
            return ResponseEntity.status(404)
                    .body(errorMap("Material nicht gefunden: " + materialId));
        } catch (Exception e) {
            log.error("Fehler beim Abrufen von Material {}: {}", materialId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap("Fehler: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // HILFSMETHODEN
    // ══════════════════════════════════════════════════════════

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("error",   message);
        return map;
    }
}
