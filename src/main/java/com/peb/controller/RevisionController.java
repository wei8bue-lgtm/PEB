package com.peb.controller;

import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.service.RevisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Revision operations
 */
@RestController
@RequestMapping("/api/revisions")
@RequiredArgsConstructor
public class RevisionController {

    private final RevisionService revisionService;

    @PostMapping("/designs/{designId}/revise")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<DesignRevision> reviseDesign(
            @PathVariable String designId,
            Authentication authentication) {
        DesignRevision revision = revisionService.reviseDesign(
            designId,
            authentication.getName()
        );
        return ResponseEntity.ok(revision);
    }

    @PostMapping("/designs/{designId}/saveas")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<Design> saveAsDesign(
            @PathVariable String designId,
            @RequestParam String newName,
            Authentication authentication) {
        Design newDesign = revisionService.saveAs(
            designId,
            newName,
            authentication.getName()
        );
        return ResponseEntity.ok(newDesign);
    }
}