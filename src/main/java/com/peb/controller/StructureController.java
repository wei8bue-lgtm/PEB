package com.peb.controller;

import com.peb.dto.StructureRelationDTO;
import com.peb.model.cad.Design;
import com.peb.model.cad.Drawing;
import com.peb.model.ebom.*;
import com.peb.model.structure.StructureNode;
import com.peb.model.structure.StructureRelation;
import com.peb.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for Structure operations.
 *
 * Changes vs. original:
 *  - addToStructure()  : forwards dto.getReferenceDesignator() to the
 *                        service (Punkt 11)
 *  - viewStructure()   : structureTree nodes now carry relationId,
 *                        referenceDesignator and bmk (populated in
 *                        StructureService.buildStructureNode – Punkt 8)
 *  - All other methods : unchanged
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class StructureController {

    private final StructureService structureService;
    private final EBOMService      ebomService;
    private final DesignService    designService;
    private final DrawingService   drawingService;

    // ─────────────────────────────────────────────────────────────────────────
    // GET /structure/{objectId}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Renders the Structure View page.
     *
     * The structureTree attribute now contains StructureNode objects that
     * include the new BMK fields (relationId, referenceDesignator, bmk).
     * These are populated by StructureService.buildStructureNode() which
     * was extended in Punkt 8.
     */
    @GetMapping("/structure/{objectId}")
    public String viewStructure(@PathVariable String objectId, Model model) {

        String prefix = objectId.substring(0, 2);
        Object mainObject;
        Long   revisionId = null;

        // ── Resolve the root object ───────────────────────────────────────
        if (prefix.equals("DS")) {
            Design design = designService.getDesign(objectId);
            mainObject = design;
            if (design.getLatestRevision() != null) {
                revisionId = design.getLatestRevision().getRevisionId();
            }

        } else if (prefix.equals("DR")) {
            Drawing drawing = drawingService.getDrawing(objectId);
            mainObject = drawing;
            if (drawing.getLatestRevision() != null) {
                revisionId = drawing.getLatestRevision().getRevisionId();
            }

        } else {
            // All eBOM object types: MA, SY, SU, MO, MU, PT, AX
            EBOMObject ebomObject = ebomService.getEBOMObject(objectId);
            mainObject = ebomObject;
            if (ebomObject.getLatestRevision() != null) {
                revisionId = ebomObject.getLatestRevision().getRevisionId();
            }
        }

        model.addAttribute("object",    mainObject);
        model.addAttribute("objectId",  objectId);
        model.addAttribute("revisionId", revisionId);

        // ── Build the structure tree (includes BMK fields) ────────────────
        if (revisionId != null) {
            StructureNode tree = structureService.getStructureTree(
                    objectId, revisionId);
            model.addAttribute("structureTree", tree);

            List<String> allowedTypes =
                    structureService.getAllowedChildTypes(objectId);
            model.addAttribute("allowedTypes", allowedTypes);
        }

        return "structure-view";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /structure/add
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds a child object to the structure.
     *
     * CHANGE (Punkt 11): dto.getReferenceDesignator() is now forwarded to
     * StructureService.addToStructure() so the user-provided partial BMK
     * segment is persisted on the StructureRelation immediately.
     */
    @PostMapping("/structure/add")
    @PreAuthorize("hasAuthority('WRITE')")
    @ResponseBody
    public ResponseEntity<?> addToStructure(
            @RequestBody StructureRelationDTO dto) {
        try {
            StructureRelation relation = structureService.addToStructure(
                    dto.getParentId(),
                    dto.getParentRevisionId(),
                    dto.getChildId(),
                    dto.getChildRevisionId(),
                    dto.getQuantity(),
                    dto.getPositionNumber(),
                    dto.getReferenceDesignator()   // ← NEW (Punkt 11)
            );
            return ResponseEntity.ok(relation);

        } catch (Exception e) {
            log.error("Error adding to structure: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /structure/{relationId}
    // ─────────────────────────────────────────────────────────────────────────

    @DeleteMapping("/structure/{relationId}")
    @PreAuthorize("hasAuthority('WRITE')")
    @ResponseBody
    public ResponseEntity<?> removeFromStructure(
            @PathVariable Long relationId) {
        try {
            structureService.removeFromStructure(relationId);
            return ResponseEntity.ok("Removed from structure");

        } catch (Exception e) {
            log.error("Error removing from structure: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/structure/{objectId}/children
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns available child objects for the Add-to-Structure form.
     * Optionally filtered by object-type prefix.
     */
    @GetMapping("/api/structure/{objectId}/children")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAvailableChildrenForType(
            @PathVariable String objectId,
            @RequestParam(required = false) String type) {

        List<String> allowedTypes =
                structureService.getAllowedChildTypes(objectId);

        if (type != null && !type.isEmpty()) {
            allowedTypes = List.of(type);
        }

        List<Map<String, Object>> children =
                getAvailableChildrenWithRevisions(allowedTypes);
        return ResponseEntity.ok(children);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers – build child lists
    // ─────────────────────────────────────────────────────────────────────────

    private List<Map<String, Object>> getAvailableChildrenWithRevisions(
            List<String> allowedTypes) {

        List<Map<String, Object>> children = new ArrayList<>();

        for (String type : allowedTypes) {
            switch (type) {
                case "MA" -> ebomService.getAllMachines()
                        .forEach(m -> children.add(createObjectMap(m)));

                case "SY" -> ebomService.getAllSystems()
                        .forEach(s -> children.add(createObjectMap(s)));

                case "SU" -> ebomService.getAllSubsystems()
                        .forEach(su -> children.add(createObjectMap(su)));

                case "MO" -> ebomService.getAllModules()
                        .forEach(mo -> children.add(createObjectMap(mo)));

                case "MU" -> ebomService.getAllSubmodules()
                        .forEach(mu -> children.add(createObjectMap(mu)));

                case "PT" -> ebomService.getAllParts()
                        .forEach(pt -> children.add(createObjectMap(pt)));

                case "AX" -> ebomService.getAllAuxiliaries()
                        .forEach(ax -> children.add(createObjectMap(ax)));

                case "DS" -> designService.getAllDesigns()
                        .forEach(ds -> children.add(createObjectMap(ds)));

                case "DR" -> drawingService.getAllDrawings()
                        .forEach(dr -> children.add(createObjectMap(dr)));

                default -> log.warn(
                        "Unknown type '{}' in getAvailableChildrenForType",
                        type);
            }
        }

        return children;
    }

    // ── Map builders ─────────────────────────────────────────────────────────

    /**
     * Builds the JSON-serialisable map for an eBOM object.
     * Used by the Add-Child dropdown in the UI.
     */
    private Map<String, Object> createObjectMap(EBOMObject obj) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",   obj.getId());
        map.put("name", obj.getName());
        map.put("type", obj.getObjectType().getDisplayName());

        if (obj.getLatestRevision() != null) {
            map.put("revisionId",    obj.getLatestRevision().getRevisionId());
            map.put("revision",      obj.getLatestRevision().getRevision());
            map.put("releaseStatus", obj.getLatestRevision()
                                        .getReleaseStatus().name());
        } else {
            map.put("revisionId",    null);
            map.put("revision",      "No revision");
            map.put("releaseStatus", "NONE");
        }

        return map;
    }

    private Map<String, Object> createObjectMap(Design design) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",   design.getId());
        map.put("name", design.getName());
        map.put("type", "Design");

        if (design.getLatestRevision() != null) {
            map.put("revisionId",    design.getLatestRevision().getRevisionId());
            map.put("revision",      design.getLatestRevision().getRevision());
            map.put("releaseStatus", design.getLatestRevision()
                                           .getReleaseStatus().name());
        } else {
            map.put("revisionId",    null);
            map.put("revision",      "No revision");
            map.put("releaseStatus", "NONE");
        }

        return map;
    }

    private Map<String, Object> createObjectMap(Drawing drawing) {
        Map<String, Object> map = new HashMap<>();
        map.put("id",   drawing.getId());
        map.put("name", drawing.getName());
        map.put("type", "Drawing");

        if (drawing.getLatestRevision() != null) {
            map.put("revisionId",    drawing.getLatestRevision().getRevisionId());
            map.put("revision",      drawing.getLatestRevision().getRevision());
            map.put("releaseStatus", drawing.getLatestRevision()
                                            .getReleaseStatus().name());
        } else {
            map.put("revisionId",    null);
            map.put("revision",      "No revision");
            map.put("releaseStatus", "NONE");
        }

        return map;
    }
}