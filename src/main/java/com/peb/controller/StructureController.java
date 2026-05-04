package com.peb.controller;

import com.peb.dto.StructureRelationDTO;
import com.peb.model.cad.Design;
import com.peb.model.cad.Drawing;
import com.peb.model.ebom.*;
import com.peb.model.structure.StructureNode;
import com.peb.model.structure.StructureRelation;
import com.peb.service.*;
import lombok.RequiredArgsConstructor;
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
 * Controller for Structure operations
 */
@Controller
@RequiredArgsConstructor
public class StructureController {

    private final StructureService structureService;
    private final EBOMService ebomService;
    private final DesignService designService;
    private final DrawingService drawingService;

    @GetMapping("/structure/{objectId}")
    public String viewStructure(@PathVariable String objectId, Model model) {
        // Get the object
        String prefix = objectId.substring(0, 2);
        Object mainObject;
        Long revisionId = null;
        
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
            EBOMObject ebomObject = ebomService.getEBOMObject(objectId);
            mainObject = ebomObject;
            if (ebomObject.getLatestRevision() != null) {
                revisionId = ebomObject.getLatestRevision().getRevisionId();
            }
        }

        model.addAttribute("object", mainObject);
        model.addAttribute("objectId", objectId);
        model.addAttribute("revisionId", revisionId);

        // Get structure tree if revision exists
        if (revisionId != null) {
            StructureNode tree = structureService.getStructureTree(objectId, revisionId);
            model.addAttribute("structureTree", tree);
            
            // Get available children to add
            List<String> allowedTypes = structureService.getAllowedChildTypes(objectId);
            model.addAttribute("allowedTypes", allowedTypes);
        }

        return "structure-view";
    }

    @PostMapping("/structure/add")
    @PreAuthorize("hasAuthority('WRITE')")
    @ResponseBody
    public ResponseEntity<?> addToStructure(@RequestBody StructureRelationDTO dto) {
        try {
            StructureRelation relation = structureService.addToStructure(
                dto.getParentId(),
                dto.getParentRevisionId(),
                dto.getChildId(),
                dto.getChildRevisionId(),
                dto.getQuantity(),
                dto.getPositionNumber()
            );
            return ResponseEntity.ok(relation);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/structure/{relationId}")
    @PreAuthorize("hasAuthority('WRITE')")
    @ResponseBody
    public ResponseEntity<?> removeFromStructure(@PathVariable Long relationId) {
        try {
            structureService.removeFromStructure(relationId);
            return ResponseEntity.ok("Removed from structure");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/structure/{objectId}/children")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAvailableChildrenForType(
            @PathVariable String objectId,
            @RequestParam(required = false) String type) {
        
        List<String> allowedTypes = structureService.getAllowedChildTypes(objectId);
        
        if (type != null && !type.isEmpty()) {
            allowedTypes = List.of(type);
        }
        
        List<Map<String, Object>> children = getAvailableChildrenWithRevisions(allowedTypes);
        return ResponseEntity.ok(children);
    }

    private List<Map<String, Object>> getAvailableChildrenWithRevisions(List<String> allowedTypes) {
        List<Map<String, Object>> children = new ArrayList<>();
        
        for (String type : allowedTypes) {
            switch (type) {
                case "MA" -> {
                    for (Machine machine : ebomService.getAllMachines()) {
                        children.add(createObjectMap(machine));
                    }
                }
                case "SY" -> {
                    for (com.peb.model.ebom.System system : ebomService.getAllSystems()) {
                        children.add(createObjectMap(system));
                    }
                }
                case "SU" -> {
                    for (Subsystem subsystem : ebomService.getAllSubsystems()) {
                        children.add(createObjectMap(subsystem));
                    }
                }
                case "MO" -> {
                    for (EBOMModule module : ebomService.getAllModules()) {
                        children.add(createObjectMap(module));
                    }
                }
                case "MU" -> {
                    for (Submodule submodule : ebomService.getAllSubmodules()) {
                        children.add(createObjectMap(submodule));
                    }
                }
                case "PT" -> {
                    for (Part part : ebomService.getAllParts()) {
                        children.add(createObjectMap(part));
                    }
                }
                case "AX" -> {
                    for (Auxiliary auxiliary : ebomService.getAllAuxiliaries()) {
                        children.add(createObjectMap(auxiliary));
                    }
                }
                case "DS" -> {
                    for (Design design : designService.getAllDesigns()) {
                        children.add(createObjectMap(design));
                    }
                }
                case "DR" -> {
                    for (Drawing drawing : drawingService.getAllDrawings()) {
                        children.add(createObjectMap(drawing));
                    }
                }
            }
        }
        
        return children;
    }

    private Map<String, Object> createObjectMap(EBOMObject obj) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", obj.getId());
        map.put("name", obj.getName());
        map.put("type", obj.getObjectType().getDisplayName());
        
        if (obj.getLatestRevision() != null) {
            map.put("revisionId", obj.getLatestRevision().getRevisionId());
            map.put("revision", obj.getLatestRevision().getRevision());
            map.put("releaseStatus", obj.getLatestRevision().getReleaseStatus().name());
        } else {
            map.put("revisionId", null);
            map.put("revision", "No revision");
            map.put("releaseStatus", "NONE");
        }
        
        return map;
    }

    private Map<String, Object> createObjectMap(Design design) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", design.getId());
        map.put("name", design.getName());
        map.put("type", "Design");
        
        if (design.getLatestRevision() != null) {
            map.put("revisionId", design.getLatestRevision().getRevisionId());
            map.put("revision", design.getLatestRevision().getRevision());
            map.put("releaseStatus", design.getLatestRevision().getReleaseStatus().name());
        } else {
            map.put("revisionId", null);
            map.put("revision", "No revision");
            map.put("releaseStatus", "NONE");
        }
        
        return map;
    }

    private Map<String, Object> createObjectMap(Drawing drawing) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", drawing.getId());
        map.put("name", drawing.getName());
        map.put("type", "Drawing");
        
        if (drawing.getLatestRevision() != null) {
            map.put("revisionId", drawing.getLatestRevision().getRevisionId());
            map.put("revision", drawing.getLatestRevision().getRevision());
            map.put("releaseStatus", drawing.getLatestRevision().getReleaseStatus().name());
        } else {
            map.put("revisionId", null);
            map.put("revision", "No revision");
            map.put("releaseStatus", "NONE");
        }
        
        return map;
    }
}