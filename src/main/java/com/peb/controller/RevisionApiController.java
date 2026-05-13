package com.peb.controller.api;

import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.model.cad.Drawing;
import com.peb.model.cad.DrawingRevision;
import com.peb.model.ebom.*;
import com.peb.repository.*;
import com.peb.service.DesignService;
import com.peb.service.DrawingService;
import com.peb.service.EBOMService;
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
 * REST API Controller für Revision-Operationen
 * Basis-URL: /api/v1/revisions
 *
 * Endpunkte:
 *   GET  /api/v1/revisions/all                    → Alle Revisionen aller Objekte
 *   GET  /api/v1/revisions/designs                → Alle Design-Revisionen
 *   GET  /api/v1/revisions/drawings               → Alle Drawing-Revisionen
 *   GET  /api/v1/revisions/ebom                   → Alle eBOM-Revisionen
 *   GET  /api/v1/revisions/designs/{designId}     → Revisionen eines Designs
 *   GET  /api/v1/revisions/drawings/{drawingId}   → Revisionen einer Zeichnung
 *   GET  /api/v1/revisions/ebom/{objectId}        → Revisionen eines eBOM-Objekts
 *   GET  /api/v1/revisions/{revisionType}/{id}    → Einzelne Revision nach ID
 */
@RestController
@RequestMapping("/api/v1/revisions")
@RequiredArgsConstructor
@Slf4j
public class RevisionApiController {

    private final DesignService  designService;
    private final DrawingService drawingService;
    private final EBOMService    ebomService;

    // Revision Repositories
    private final DesignRevisionRepository      designRevisionRepository;
    private final DrawingRevisionRepository     drawingRevisionRepository;
    private final MachineRevisionRepository     machineRevisionRepository;
    private final SystemRevisionRepository      systemRevisionRepository;
    private final SubsystemRevisionRepository   subsystemRevisionRepository;
    private final EBOMModuleRevisionRepository  ebomModuleRevisionRepository;
    private final SubmoduleRevisionRepository   submoduleRevisionRepository;
    private final PartRevisionRepository        partRevisionRepository;
    private final AuxiliaryRevisionRepository   auxiliaryRevisionRepository;

    // ══════════════════════════════════════════════════════════
    // ALLE REVISIONEN - Übersicht
    // GET /api/v1/revisions/all
    // GET /api/v1/revisions/all?objectType=DESIGN
    // GET /api/v1/revisions/all?releaseStatus=WORKING
    // ══════════════════════════════════════════════════════════

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAllRevisions(
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String releaseStatus) {

        try {
            List<Map<String, Object>> allRevisions = new ArrayList<>();

            // Filter nach objectType
            boolean loadDesigns   = objectType == null || objectType.equalsIgnoreCase("DESIGN");
            boolean loadDrawings  = objectType == null || objectType.equalsIgnoreCase("DRAWING");
            boolean loadEbom      = objectType == null
                                    || objectType.equalsIgnoreCase("EBOM")
                                    || objectType.equalsIgnoreCase("MACHINE")
                                    || objectType.equalsIgnoreCase("SYSTEM")
                                    || objectType.equalsIgnoreCase("SUBSYSTEM")
                                    || objectType.equalsIgnoreCase("MODULE")
                                    || objectType.equalsIgnoreCase("SUBMODULE")
                                    || objectType.equalsIgnoreCase("PART")
                                    || objectType.equalsIgnoreCase("AUXILIARY");

            if (loadDesigns) {
                designRevisionRepository.findAll().forEach(rev ->
                    allRevisions.add(toDesignRevisionMap(rev)));
            }

            if (loadDrawings) {
                drawingRevisionRepository.findAll().forEach(rev ->
                    allRevisions.add(toDrawingRevisionMap(rev)));
            }

            if (loadEbom) {
                addEbomRevisions(allRevisions, objectType);
            }

            // Filter nach releaseStatus
            List<Map<String, Object>> filtered = allRevisions;
            if (releaseStatus != null && !releaseStatus.isEmpty()) {
                final String statusFilter = releaseStatus.toUpperCase();
                filtered = allRevisions.stream()
                        .filter(r -> statusFilter.equals(r.get("releaseStatus")))
                        .toList();
            }

            // Sortieren nach ObjectId + RevisionId
            filtered = filtered.stream()
                    .sorted((a, b) -> {
                        String objA = String.valueOf(a.get("objectId"));
                        String objB = String.valueOf(b.get("objectId"));
                        int cmp = objA.compareTo(objB);
                        if (cmp != 0) return cmp;
                        return String.valueOf(a.get("revision"))
                                     .compareTo(String.valueOf(b.get("revision")));
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("count",     filtered.size());
            response.put("revisions", filtered);

            log.info("GET /api/v1/revisions/all → {} Revisionen", filtered.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler beim Abrufen aller Revisionen: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(errorMap("Fehler: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // DESIGN REVISIONEN
    // GET /api/v1/revisions/designs
    // GET /api/v1/revisions/designs/{designId}
    // ══════════════════════════════════════════════════════════

    @GetMapping("/designs")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAllDesignRevisions() {
        try {
            List<Map<String, Object>> revisions = designRevisionRepository.findAll()
                    .stream()
                    .map(this::toDesignRevisionMap)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("count",     revisions.size());
            response.put("revisions", revisions);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler Design-Revisionen: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap(e.getMessage()));
        }
    }

    @GetMapping("/designs/{designId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getDesignRevisions(@PathVariable String designId) {
        try {
            List<Map<String, Object>> revisions =
                    designRevisionRepository
                            .findByDesignIdOrderByRevisionDesc(designId)
                            .stream()
                            .map(this::toDesignRevisionMap)
                            .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("objectId",  designId);
            response.put("count",     revisions.size());
            response.put("revisions", revisions);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler Design-Revisionen für {}: {}", designId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // DRAWING REVISIONEN
    // GET /api/v1/revisions/drawings
    // GET /api/v1/revisions/drawings/{drawingId}
    // ══════════════════════════════════════════════════════════

    @GetMapping("/drawings")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAllDrawingRevisions() {
        try {
            List<Map<String, Object>> revisions = drawingRevisionRepository.findAll()
                    .stream()
                    .map(this::toDrawingRevisionMap)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("count",     revisions.size());
            response.put("revisions", revisions);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler Drawing-Revisionen: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap(e.getMessage()));
        }
    }

    @GetMapping("/drawings/{drawingId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getDrawingRevisions(@PathVariable String drawingId) {
        try {
            List<Map<String, Object>> revisions =
                    drawingRevisionRepository
                            .findByDrawingIdOrderByRevisionDesc(drawingId)
                            .stream()
                            .map(this::toDrawingRevisionMap)
                            .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("objectId",  drawingId);
            response.put("count",     revisions.size());
            response.put("revisions", revisions);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler Drawing-Revisionen für {}: {}", drawingId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // EBOM REVISIONEN
    // GET /api/v1/revisions/ebom
    // GET /api/v1/revisions/ebom/{objectId}
    // ══════════════════════════════════════════════════════════

    @GetMapping("/ebom")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getAllEbomRevisions() {
        try {
            List<Map<String, Object>> revisions = new ArrayList<>();
            addEbomRevisions(revisions, null);

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("count",     revisions.size());
            response.put("revisions", revisions);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler eBOM-Revisionen: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap(e.getMessage()));
        }
    }

    @GetMapping("/ebom/{objectId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getEbomRevisions(@PathVariable String objectId) {
        try {
            String prefix = objectId.substring(0, 2).toUpperCase();
            List<Map<String, Object>> revisions = getEbomRevisionsByPrefix(objectId, prefix);

            Map<String, Object> response = new HashMap<>();
            response.put("success",   true);
            response.put("objectId",  objectId);
            response.put("count",     revisions.size());
            response.put("revisions", revisions);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler eBOM-Revisionen für {}: {}", objectId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // EINZELNE REVISION
    // GET /api/v1/revisions/DESIGN/123
    // GET /api/v1/revisions/EBOM/456
    // ══════════════════════════════════════════════════════════

    @GetMapping("/{revisionType}/{revisionId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getSingleRevision(
            @PathVariable String revisionType,
            @PathVariable Long   revisionId) {
        try {
            Map<String, Object> revision = switch (revisionType.toUpperCase()) {
                case "DESIGN" -> designRevisionRepository.findById(revisionId)
                        .map(this::toDesignRevisionMap)
                        .orElse(null);
                case "DRAWING" -> drawingRevisionRepository.findById(revisionId)
                        .map(this::toDrawingRevisionMap)
                        .orElse(null);
                case "EBOM", "MACHINE" -> machineRevisionRepository.findById(revisionId)
                        .map(this::toEbomRevisionMap)
                        .orElse(null);
                default -> null;
            };

            if (revision == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success",  true);
            response.put("revision", revision);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fehler Einzelrevision {}/{}: {}", revisionType, revisionId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(errorMap(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════
    // HILFSMETHODEN - Mapping
    // ══════════════════════════════════════════════════════════

    private Map<String, Object> toDesignRevisionMap(DesignRevision rev) {
        Map<String, Object> map = new HashMap<>();
        map.put("revisionId",    rev.getRevisionId());
        map.put("objectId",      rev.getDesign() != null ? rev.getDesign().getId() : null);
        map.put("objectName",    rev.getName());
        map.put("objectType",    "DESIGN");
        map.put("revision",      rev.getRevision());
        map.put("releaseStatus", rev.getReleaseStatus() != null
                                  ? rev.getReleaseStatus().name() : null);
        map.put("department",    rev.getDepartment());
        map.put("mass",          rev.getMass());
        map.put("material",      rev.getMaterial());
        map.put("checkedOut",    rev.isCheckedOut());
        map.put("checkedOutBy",  rev.getCheckedOutBy());
        map.put("createdDate",   rev.getCreatedDate() != null
                                  ? rev.getCreatedDate().toString() : null);
        map.put("createdBy",     rev.getCreatedBy());
        map.put("lastModified",  rev.getLastModifiedDate() != null
                                  ? rev.getLastModifiedDate().toString() : null);
        map.put("lastModifiedBy", rev.getLastModifiedBy());
        // Dataset-URL für VBScript
        map.put("datasetsUrl",  "/api/v1/datasets?revisionId="
                                 + rev.getRevisionId() + "&revisionType=DESIGN");
        return map;
    }

    private Map<String, Object> toDrawingRevisionMap(DrawingRevision rev) {
        Map<String, Object> map = new HashMap<>();
        map.put("revisionId",    rev.getRevisionId());
        map.put("objectId",      rev.getDrawing() != null ? rev.getDrawing().getId() : null);
        map.put("objectName",    rev.getName());
        map.put("objectType",    "DRAWING");
        map.put("revision",      rev.getRevision());
        map.put("releaseStatus", rev.getReleaseStatus() != null
                                  ? rev.getReleaseStatus().name() : null);
        map.put("department",    rev.getDepartment());
        map.put("checkedOut",    rev.isCheckedOut());
        map.put("checkedOutBy",  rev.getCheckedOutBy());
        map.put("createdDate",   rev.getCreatedDate() != null
                                  ? rev.getCreatedDate().toString() : null);
        map.put("createdBy",     rev.getCreatedBy());
        map.put("lastModified",  rev.getLastModifiedDate() != null
                                  ? rev.getLastModifiedDate().toString() : null);
        map.put("lastModifiedBy", rev.getLastModifiedBy());
        map.put("datasetsUrl",   "/api/v1/datasets?revisionId="
                                  + rev.getRevisionId() + "&revisionType=DRAWING");
        return map;
    }

    private Map<String, Object> toEbomRevisionMap(EBOMRevision rev) {
        Map<String, Object> map = new HashMap<>();
        map.put("revisionId",    rev.getRevisionId());
        map.put("objectId",      rev.getObjectId());
        map.put("objectName",    rev.getName());
        map.put("objectType",    rev.getEbomObject() != null
                                  ? rev.getEbomObject().getObjectType().name() : "EBOM");
        map.put("revision",      rev.getRevision());
        map.put("releaseStatus", rev.getReleaseStatus() != null
                                  ? rev.getReleaseStatus().name() : null);
        map.put("department",    rev.getDepartment());
        map.put("mass",          rev.getMass());
        map.put("material",      rev.getMaterial());
        map.put("supplier",      rev.getSupplier());
        map.put("checkedOut",    rev.isCheckedOut());
        map.put("checkedOutBy",  rev.getCheckedOutBy());
        map.put("createdDate",   rev.getCreatedDate() != null
                                  ? rev.getCreatedDate().toString() : null);
        map.put("createdBy",     rev.getCreatedBy());
        map.put("lastModified",  rev.getLastModifiedDate() != null
                                  ? rev.getLastModifiedDate().toString() : null);
        map.put("lastModifiedBy", rev.getLastModifiedBy());
        map.put("datasetsUrl",   "/api/v1/datasets?revisionId="
                                  + rev.getRevisionId() + "&revisionType=EBOM");
        return map;
    }

    // eBOM Revisionen aller Typen sammeln
    private void addEbomRevisions(List<Map<String, Object>> list, String typeFilter) {
        boolean all = typeFilter == null
                      || typeFilter.equalsIgnoreCase("EBOM");

        if (all || typeFilter.equalsIgnoreCase("MACHINE")) {
            machineRevisionRepository.findAll()
                    .forEach(r -> list.add(toEbomRevisionMap(r)));
        }
        if (all || typeFilter.equalsIgnoreCase("SYSTEM")) {
            systemRevisionRepository.findAll()
                    .forEach(r -> list.add(toEbomRevisionMap(r)));
        }
        if (all || typeFilter.equalsIgnoreCase("SUBSYSTEM")) {
            subsystemRevisionRepository.findAll()
                    .forEach(r -> list.add(toEbomRevisionMap(r)));
        }
        if (all || typeFilter.equalsIgnoreCase("MODULE")) {
            ebomModuleRevisionRepository.findAll()
                    .forEach(r -> list.add(toEbomRevisionMap(r)));
        }
        if (all || typeFilter.equalsIgnoreCase("SUBMODULE")) {
            submoduleRevisionRepository.findAll()
                    .forEach(r -> list.add(toEbomRevisionMap(r)));
        }
        if (all || typeFilter.equalsIgnoreCase("PART")) {
            partRevisionRepository.findAll()
                    .forEach(r -> list.add(toEbomRevisionMap(r)));
        }
        if (all || typeFilter.equalsIgnoreCase("AUXILIARY")) {
            auxiliaryRevisionRepository.findAll()
                    .forEach(r -> list.add(toEbomRevisionMap(r)));
        }
    }

    // eBOM Revisionen für ein bestimmtes Objekt
    private List<Map<String, Object>> getEbomRevisionsByPrefix(
            String objectId, String prefix) {
        return switch (prefix) {
            case "MA" -> machineRevisionRepository
                    .findByMachineIdOrderByRevisionDesc(objectId)
                    .stream().map(this::toEbomRevisionMap).toList();
            case "SY" -> systemRevisionRepository
                    .findBySystemIdOrderByRevisionDesc(objectId)
                    .stream().map(this::toEbomRevisionMap).toList();
            case "SU" -> subsystemRevisionRepository
                    .findBySubsystemIdOrderByRevisionDesc(objectId)
                    .stream().map(this::toEbomRevisionMap).toList();
            case "MO" -> ebomModuleRevisionRepository
                    .findByModuleIdOrderByRevisionDesc(objectId)
                    .stream().map(this::toEbomRevisionMap).toList();
            case "MU" -> submoduleRevisionRepository
                    .findBySubmoduleIdOrderByRevisionDesc(objectId)
                    .stream().map(this::toEbomRevisionMap).toList();
            case "PT" -> partRevisionRepository
                    .findByPartIdOrderByRevisionDesc(objectId)
                    .stream().map(this::toEbomRevisionMap).toList();
            case "AX" -> auxiliaryRevisionRepository
                    .findByAuxiliaryIdOrderByRevisionDesc(objectId)
                    .stream().map(this::toEbomRevisionMap).toList();
            default -> List.of();
        };
    }

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("error",   message);
        return map;
    }
}