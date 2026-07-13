package com.peb.controller.api;

import com.peb.dto.EclassClassDTO;
import com.peb.dto.EclassSimilarResultDTO;
import com.peb.service.EclassService;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST-API für ECLASS-Funktionen (Autocomplete, AJAX-Browsing, Wiederverwendungssuche).
 */
@RestController
@RequestMapping("/api/v1/eclass")
@RequiredArgsConstructor
public class EclassApiController {

    private final EclassService eclassService;

    /**
     * Volltextsuche im ECLASS-Katalog (für Autocomplete-Dropdown).
     * GET /api/v1/eclass/search?term=motor
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<EclassClassDTO>> search(@RequestParam String term) {
        return ResponseEntity.ok(eclassService.searchClasses(term));
    }

    /**
     * Hierarchie-Browser: gibt Kindklassen einer bestimmten Ebene zurück.
     * GET /api/v1/eclass/browse?level=maingroup&parent=27
     */
    @GetMapping("/browse")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<EclassClassDTO>> browse(@RequestParam String level,
                                                        @RequestParam String parent) {
        List<EclassClassDTO> result = switch (level.toLowerCase()) {
            case "maingroup" -> eclassService.getMainGroups(parent);
            case "group"     -> eclassService.getGroups(parent);
            case "commodity" -> eclassService.getCommodities(parent);
            default          -> List.of();
        };
        return ResponseEntity.ok(result);
    }

    /**
     * ECLASS-Klassen-Detail inkl. Merkmale.
     * GET /api/v1/eclass/classes/{classId}
     */
    @GetMapping("/classes/{classId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<EclassClassDTO> getClass(@PathVariable Long classId) {
        return ResponseEntity.ok(eclassService.getEclassClass(classId));
    }

    /**
     * Aktuelle Klassifizierung eines Objekts.
     * GET /api/v1/eclass/classification/DESIGN/DS00000001
     */
    @GetMapping("/classification/{objectType}/{objectId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getClassification(@PathVariable String objectType,
                                                @PathVariable String objectId) {
        ObjectType type = ObjectType.valueOf(objectType.toUpperCase());
        var classification = eclassService.getClassification(objectId, type);
        if (classification == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(classification);
    }

    /**
     * Ähnliche Objekte (Wiederverwendungssuche).
     * GET /api/v1/eclass/similar/PART/PT00000042
     */
    @GetMapping("/similar/{objectType}/{objectId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<EclassSimilarResultDTO>> getSimilar(@PathVariable String objectType,
                                                                     @PathVariable String objectId) {
        ObjectType type = ObjectType.valueOf(objectType.toUpperCase());
        return ResponseEntity.ok(eclassService.findSimilar(objectId, type));
    }

    /**
     * Alle Objekte mit einem bestimmten ECLASS-Code.
     * GET /api/v1/eclass/objects/27-37-04-01
     */
    @GetMapping("/objects/{code}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<EclassSimilarResultDTO>> getObjectsByCode(@PathVariable String code) {
        return ResponseEntity.ok(eclassService.findByEclassCode(code));
    }
}
