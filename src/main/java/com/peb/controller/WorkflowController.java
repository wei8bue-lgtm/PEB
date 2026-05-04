package com.peb.controller;

import com.peb.model.ebom.EBOMRevision;
import com.peb.service.EBOMRevisionService;
import com.peb.service.EBOMWorkflowService;
import com.peb.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Workflow operations
 */
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final EBOMWorkflowService ebomWorkflowService;
    private final EBOMRevisionService ebomRevisionService;

    @PostMapping("/designs/{designId}/revisions/{revisionId}/release")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public ResponseEntity<Void> releaseDesignRevision(
            @PathVariable String designId,
            @PathVariable Long revisionId,
            Authentication authentication) {
        workflowService.releaseDesignRevision(designId, revisionId, authentication.getName());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/ebom/{objectId}/release")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public ResponseEntity<String> releaseEBOMRevision(
            @PathVariable String objectId,
            Authentication authentication) {
        try {
            ebomWorkflowService.releaseEBOMRevision(objectId, authentication.getName());
            return ResponseEntity.accepted().body("Release workflow started. You will receive an email notification.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Release failed: " + e.getMessage());
        }
    }

    @PostMapping("/ebom/{objectId}/revise")
    @PreAuthorize("hasAuthority('EXECUTE')")
    public ResponseEntity<String> reviseEBOMObject(
            @PathVariable String objectId,
            Authentication authentication) {
        try {
            EBOMRevision newRevision = ebomRevisionService.reviseEBOMObject(
                objectId, 
                authentication.getName()
            );
            return ResponseEntity.ok("New revision " + newRevision.getRevision() + " created successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to create revision: " + e.getMessage());
        }
    }
}