package com.peb.controller;

import com.peb.dto.DesignStructureNodeDTO;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignStructure;
import com.peb.service.DesignService;
import com.peb.service.DesignStructureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException; 

@Controller
@RequestMapping("/designs/structure")
@RequiredArgsConstructor
@Slf4j
public class DesignStructureController {

    private final DesignStructureService structureService;
    private final DesignService          designService;

    // ══════════════════════════════════════════════════════════════════════
    // VIEW
    // ══════════════════════════════════════════════════════════════════════

    /**
     * GET /designs/structure/{id}
     * Renders the Design Structure page for the given Design.
     */
	@GetMapping("/{id}")
	public String viewStructure(@PathVariable String id, Model model) {

		Design design = designService.getDesign(id);
		model.addAttribute("design", design);

		Long revisionId = design.getLatestRevision() != null
				? design.getLatestRevision().getRevisionId()
				: null;
		model.addAttribute("revisionId", revisionId);

		if (revisionId != null) {
			DesignStructureNodeDTO tree = structureService.buildTree(id);
			model.addAttribute("structureTree", tree);

			// Flache Liste für Thymeleaf – kein rekursives Fragment nötig
			List<DesignStructureNodeDTO> flatList =
					structureService.flattenTree(tree);
			model.addAttribute("flatList", flatList);
		}

		return "designs/design-structure";
	}

    // ══════════════════════════════════════════════════════════════════════
    // REST – available children
    // ══════════════════════════════════════════════════════════════════════

    /**
     * GET /designs/structure/{id}/available-children
     * Returns a JSON array of Designs eligible to be added as children.
     */
    @GetMapping("/{id}/available-children")
    @ResponseBody
    public ResponseEntity<List<DesignStructureNodeDTO>> availableChildren(
            @PathVariable String id) {

        Design design = designService.getDesign(id);

        if (design.getLatestRevision() == null) {
            // No revision → nothing can be linked
            return ResponseEntity.ok(List.of());
        }

        List<DesignStructureNodeDTO> candidates =
                structureService.getAvailableChildren(
                        design.getLatestRevision().getRevisionId(),
                        id);

        return ResponseEntity.ok(candidates);
    }

    // ══════════════════════════════════════════════════════════════════════
    // REST – add child
    // ══════════════════════════════════════════════════════════════════════

    /**
     * POST /designs/structure/add
     * Body: {@link AddChildRequest} (JSON)
     *
     * Creates a new parent → child link in DESIGN_STRUCTURE.
     */
    @PostMapping("/add")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> addChild(@RequestBody AddChildRequest req) {
        try {
            DesignStructure link = structureService.addChild(
                    req.parentId(),
                    req.parentRevisionId(),
                    req.childId(),
                    req.childRevisionId(),
                    req.quantity(),
                    req.positionNumber(),
                    req.translationX(),
                    req.translationY(),
                    req.translationZ(),
                    req.rotationI(),
                    req.rotationJ(),
                    req.rotationK());

            return ResponseEntity.ok(Map.of(
                    "structureId", link.getStructureId(),
                    "message",     "Child design added successfully."));

        } catch (IllegalArgumentException e) {
            log.warn("addChild rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // REST – placement aktualisieren
    // ══════════════════════════════════════════════════════════════════════

    /**
     * PATCH /designs/structure/{structureId}/placement
     * Updates Translation X/Y/Z and Rotation I/J/K on an existing link.
     */
    @PatchMapping("/{structureId}/placement")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> updatePlacement(@PathVariable Long structureId,
                                             @RequestBody PlacementRequest req) {
        try {
            structureService.updatePlacement(structureId,
                    req.translationX(), req.translationY(), req.translationZ(),
                    req.rotationI(),    req.rotationJ(),    req.rotationK());
            return ResponseEntity.ok(Map.of("success", true,
                    "structureId", structureId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // REST – remove child
    // ══════════════════════════════════════════════════════════════════════

    /**
     * DELETE /designs/structure/{structureId}
     * Removes one structure link. The child Design is NOT deleted.
     */
    @DeleteMapping("/{structureId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<?> removeChild(@PathVariable Long structureId) {
        try {
            structureService.removeChild(structureId);
            return ResponseEntity.ok(
                    Map.of("message", "Structure link removed."));
        } catch (NoSuchElementException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Request record
    // ══════════════════════════════════════════════════════════════════════

    /**
     * JSON body for POST /designs/structure/add.
     */
    record AddChildRequest(
            String  parentId,
            Long    parentRevisionId,
            String  childId,
            Long    childRevisionId,
            int     quantity,
            String  positionNumber,
            Double  translationX,
            Double  translationY,
            Double  translationZ,
            Double  rotationI,
            Double  rotationJ,
            Double  rotationK
    ) {}

    record PlacementRequest(
            Double translationX, Double translationY, Double translationZ,
            Double rotationI,    Double rotationJ,    Double rotationK
    ) {}
}