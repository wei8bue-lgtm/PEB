package com.peb.controller.api;

import com.peb.dto.DesignDTO;
import com.peb.model.cad.Design;
import com.peb.service.DesignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API Controller for Design operations
 */
@RestController
@RequestMapping("/api/designs")
@RequiredArgsConstructor
public class DesignRestController {

    private final DesignService designService;

    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<Design>> getAllDesigns() {
        List<Design> designs = designService.getAllDesigns();
        return ResponseEntity.ok(designs);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Design> getDesign(@PathVariable String id) {
        Design design = designService.getDesign(id);
        return ResponseEntity.ok(design);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<Design> createDesign(@RequestBody DesignDTO designDTO) {
        Design design = designService.createDesign(
            designDTO.getName(),
            designDTO.getDepartment()
        );
        return ResponseEntity.ok(design);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<Design> updateDesign(
            @PathVariable String id, 
            @RequestBody DesignDTO designDTO) {
        Design design = designService.updateDesign(
            id,
            designDTO.getName(),
            designDTO.getDepartment(),
            designDTO.getMass(),
            designDTO.getMaterial()
        );
        return ResponseEntity.ok(design);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE')")
    public ResponseEntity<Void> deleteDesign(@PathVariable String id) {
        designService.deleteDesign(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<Design>> searchDesigns(@RequestParam String query) {
        List<Design> designs = designService.searchDesigns(query);
        return ResponseEntity.ok(designs);
    }
}