package com.peb.controller;

import com.peb.service.SynchronizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for synchronization operations
 */
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SynchronizationController {

    private final SynchronizationService synchronizationService;

    @PostMapping("/mass")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<String> syncMass(@RequestParam String designId, @RequestParam String ebomId) {
        synchronizationService.syncMassFromDesign(designId, ebomId);
        return ResponseEntity.ok("Mass synchronized from Design to eBOM");
    }

    @PostMapping("/material")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<String> syncMaterial(@RequestParam String ebomId, @RequestParam String designId) {
        synchronizationService.syncMaterialToDesign(ebomId, designId);
        return ResponseEntity.ok("Material synchronized from eBOM to Design");
    }

    @PostMapping("/full")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<String> syncFull(@RequestParam String designId, @RequestParam String ebomId) {
        synchronizationService.performFullSync(designId, ebomId);
        return ResponseEntity.ok("Full bi-directional synchronization completed");
    }
}