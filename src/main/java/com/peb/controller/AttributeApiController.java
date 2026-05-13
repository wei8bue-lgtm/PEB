package com.peb.controller.api;

import com.peb.dto.AttributeDTO;
import com.peb.exception.PEBException;
import com.peb.service.AttributeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API für Attribut-Verwaltung
 * Optimiert für VBScript / externe Clients
 *
 * Basis-URL: /api/v1/attributes
 *
 * ENDPUNKTE:
 *   GET    /api/v1/attributes/{objectId}
 *   GET    /api/v1/attributes/{objectId}/{attributeName}
 *   PUT    /api/v1/attributes/{objectId}/{attributeName}
 *   PATCH  /api/v1/attributes/{objectId}
 *   GET    /api/v1/attributes/{objectId}/schema
 */
@RestController
@RequestMapping("/api/v1/attributes")
@RequiredArgsConstructor
@Slf4j
public class AttributeApiController {

    private final AttributeService attributeService;

    // ══════════════════════════════════════════════════════
    // GET ALL - Alle Attribute eines Objekts
    // GET /api/v1/attributes/DS00000001
    // ══════════════════════════════════════════════════════

    @GetMapping("/{objectId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAllAttributes(@PathVariable String objectId) {
        try {
            Map<String, String> attributes = attributeService.getAttributes(objectId);

            Map<String, Object> response = new HashMap<>();
            response.put("success",    true);
            response.put("objectId",   objectId);
            response.put("count",      attributes.size());
            response.put("attributes", attributes);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler beim Lesen der Attribute für {}: {}", objectId, e.getMessage());
            return ResponseEntity.badRequest().body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // GET ONE - Einzelnes Attribut lesen
    // GET /api/v1/attributes/DS00000001/name
    // ══════════════════════════════════════════════════════

    @GetMapping("/{objectId}/{attributeName}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAttribute(
            @PathVariable String objectId,
            @PathVariable String attributeName) {
        try {
            String value = attributeService.getAttribute(objectId, attributeName);

            Map<String, Object> response = new HashMap<>();
            response.put("success",       true);
            response.put("objectId",      objectId);
            response.put("attributeName", attributeName);
            response.put("value",         value);

            return ResponseEntity.ok(response);

        } catch (PEBException e) {
            return ResponseEntity.badRequest().body(errorMap(e.getMessage()));
        } catch (Exception e) {
            log.error("Fehler beim Lesen von {}/{}: {}", objectId, attributeName, e.getMessage());
            return ResponseEntity.status(500).body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // PUT - Einzelnes Attribut setzen
    // PUT /api/v1/attributes/DS00000001/name
    // Body: { "value": "Neuer Name" }
    // ══════════════════════════════════════════════════════

    @PutMapping("/{objectId}/{attributeName}")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> setAttribute(
            @PathVariable String objectId,
            @PathVariable String attributeName,
            @RequestBody  Map<String, String> body) {
        try {
            String value = body.get("value");
            if (value == null) {
                return ResponseEntity.badRequest()
                        .body(errorMap("Body muss 'value' enthalten"));
            }

            Map<String, String> updated =
                    attributeService.setAttribute(objectId, attributeName, value);

            Map<String, Object> response = new HashMap<>();
            response.put("success",       true);
            response.put("message",       "Attribut aktualisiert: " + attributeName);
            response.put("objectId",      objectId);
            response.put("attributeName", attributeName);
            response.put("newValue",      value);
            response.put("attributes",    updated);

            return ResponseEntity.ok(response);

        } catch (PEBException e) {
            return ResponseEntity.badRequest().body(errorMap(e.getMessage()));
        } catch (Exception e) {
            log.error("Fehler beim Setzen von {}/{}: {}", objectId, attributeName, e.getMessage());
            return ResponseEntity.status(500).body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // PATCH - Mehrere Attribute gleichzeitig setzen (Bulk)
    // PATCH /api/v1/attributes/DS00000001
    // Body: { "name": "Neuer Name", "mass": "150", "department": "R&D" }
    // ══════════════════════════════════════════════════════

    @PatchMapping("/{objectId}")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> setAttributes(
            @PathVariable String objectId,
            @RequestBody  Map<String, String> attributes) {
        try {
            if (attributes == null || attributes.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(errorMap("Keine Attribute angegeben"));
            }

            Map<String, String> updated =
                    attributeService.setAttributes(objectId, attributes);

            Map<String, Object> response = new HashMap<>();
            response.put("success",          true);
            response.put("message",          attributes.size() + " Attribute aktualisiert");
            response.put("objectId",         objectId);
            response.put("updatedCount",     attributes.size());
            response.put("updatedAttributes", attributes.keySet());
            response.put("attributes",       updated);

            return ResponseEntity.ok(response);

        } catch (PEBException e) {
            return ResponseEntity.badRequest().body(errorMap(e.getMessage()));
        } catch (Exception e) {
            log.error("Fehler beim Bulk-Update für {}: {}", objectId, e.getMessage());
            return ResponseEntity.status(500).body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════
    // SCHEMA - Welche Attribute gibt es / welche sind editierbar?
    // GET /api/v1/attributes/DS00000001/schema
    // ══════════════════════════════════════════════════════

    @GetMapping("/{objectId}/schema")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAttributeSchema(@PathVariable String objectId) {
        try {
            Map<String, Object> schema =
                    attributeService.getAttributeSchema(objectId);

            Map<String, Object> response = new HashMap<>();
            response.put("success",  true);
            response.put("objectId", objectId);
            response.put("schema",   schema);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(errorMap(e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────
    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("error",   message);
        return map;
    }
}