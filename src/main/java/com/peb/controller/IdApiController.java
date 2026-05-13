package com.peb.controller.api;

import com.peb.repository.DesignRepository;
import com.peb.repository.DrawingRepository;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API für ID-Verwaltung
 * Gibt die nächste freie ID für verschiedene Objekttypen zurück
 */
@RestController
@RequestMapping("/api/v1/ids")
@RequiredArgsConstructor
@Slf4j
public class IdApiController {

    private final DesignRepository  designRepository;
    private final DrawingRepository drawingRepository;

    // ══════════════════════════════════════════════════════
    // NÄCHSTE ID - Einzelner Typ
    // GET /api/v1/ids/next?type=DESIGN
    // GET /api/v1/ids/next?type=DRAWING
    // ══════════════════════════════════════════════════════

    @GetMapping("/next")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getNextId(@RequestParam String type) {
        try {
            String nextId = calculateNextId(type.toUpperCase());

            Map<String, Object> response = new HashMap<>();
            response.put("success",    true);
            response.put("type",       type.toUpperCase());
            response.put("nextId",     nextId);
            response.put("prefix",     nextId.substring(0, 2));
            response.put("sequence",   Long.parseLong(nextId.substring(2)));

            log.info("Nächste ID abgefragt: type={}, nextId={}", type, nextId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error",   "Unbekannter Typ: " + type +
                               " | Erlaubt: DESIGN, DRAWING, MACHINE, " +
                               "SYSTEM, SUBSYSTEM, MODULE, SUBMODULE, PART, AUXILIARY"
            ));
        } catch (Exception e) {
            log.error("Fehler bei ID-Abfrage für Typ {}: {}", type, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error",   "Fehler: " + e.getMessage()
            ));
        }
    }

    // ══════════════════════════════════════════════════════
    // NÄCHSTE IDs - Alle Typen auf einmal
    // GET /api/v1/ids/next/all
    // ══════════════════════════════════════════════════════

    @GetMapping("/next/all")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAllNextIds() {
        try {
            Map<String, Object> ids = new HashMap<>();
            ids.put("DESIGN",  calculateNextId("DESIGN"));
            ids.put("DRAWING", calculateNextId("DRAWING"));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("ids",     ids);

            log.info("Alle nächsten IDs abgefragt: {}", ids);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler bei getAllNextIds: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error",   "Fehler: " + e.getMessage()
            ));
        }
    }

    // ══════════════════════════════════════════════════════
    // AKTUELLE MAX-ID - Letzte vergebene ID
    // GET /api/v1/ids/current?type=DESIGN
    // ══════════════════════════════════════════════════════

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getCurrentId(@RequestParam String type) {
        try {
            long maxSeq = getMaxSequence(type.toUpperCase());

            String currentId = "Noch keine ID vergeben";
            if (maxSeq > 0) {
                currentId = ObjectType.valueOf(type.toUpperCase()).formatId(maxSeq);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("type",      type.toUpperCase());
            response.put("currentId", currentId);
            response.put("sequence",  maxSeq);
            response.put("count",     getCount(type.toUpperCase()));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error",   "Unbekannter Typ: " + type
            ));
        }
    }

    // ──────────────────────────────────────────────────────
    // HILFSMETHODEN
    // ──────────────────────────────────────────────────────

    private String calculateNextId(String type) {
        long nextSeq = getMaxSequence(type) + 1;
        return ObjectType.valueOf(type).formatId(nextSeq);
    }

    private long getMaxSequence(String type) {
        Long maxSeq = switch (type) {
            case "DESIGN"  -> designRepository.findMaxSequence();
            case "DRAWING" -> drawingRepository.findMaxSequence();
            default -> throw new IllegalArgumentException("Unbekannter Typ: " + type);
        };
        return maxSeq != null ? maxSeq : 0L;
    }

    private long getCount(String type) {
        return switch (type) {
            case "DESIGN"  -> designRepository.count();
            case "DRAWING" -> drawingRepository.count();
            default -> throw new IllegalArgumentException("Unbekannter Typ: " + type);
        };
    }
}