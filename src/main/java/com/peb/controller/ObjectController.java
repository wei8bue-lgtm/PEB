package com.peb.controller;

import com.peb.model.cad.Design;
import com.peb.model.cad.Drawing;
import com.peb.model.ebom.EBOMObject;
import com.peb.service.DesignService;
import com.peb.service.DrawingService;
import com.peb.service.EBOMService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Generic object controller for accessing any object by ID
 */
@RestController
@RequestMapping("/api/objects")
@RequiredArgsConstructor
public class ObjectController {

    private final DesignService designService;
    private final DrawingService drawingService;
    private final EBOMService ebomService;

    @GetMapping("/{objectId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<?> getObject(@PathVariable String objectId) {
        String prefix = objectId.substring(0, 2);
        
        try {
            Object obj = switch (prefix) {
                case "DS" -> designService.getDesign(objectId);
                case "DR" -> drawingService.getDrawing(objectId);
                default -> ebomService.getEBOMObject(objectId);
            };
            
            return ResponseEntity.ok(obj);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}