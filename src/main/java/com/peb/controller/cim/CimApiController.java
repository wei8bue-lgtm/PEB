package com.peb.controller.cim;

import com.peb.dto.cim.CimImportRequestDTO;
import com.peb.dto.cim.CimReleaseRequestDTO;
import com.peb.model.cim.CimManufacturingLink;
import com.peb.model.cim.CimObject;
import com.peb.model.cim.CimRevision;
import com.peb.model.cim.CimValidationResult;
import com.peb.service.cim.CimService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cim")
@RequiredArgsConstructor
public class CimApiController {

    private final CimService cimService;

    @PostMapping("/from-mae/{maeObjectId}")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<CimObject> createFromMae(@PathVariable String maeObjectId) {
        return ResponseEntity.ok(cimService.createFromMae(maeObjectId));
    }

    @PostMapping("/{cimId}/sync")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<CimRevision> syncFromMae(@PathVariable String cimId) {
        return ResponseEntity.ok(cimService.syncFromMae(cimId));
    }

    @PostMapping("/revisions/{revisionId}/review")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<CimRevision> submitForReview(@PathVariable Long revisionId) {
        return ResponseEntity.ok(cimService.submitForReview(revisionId));
    }

    @PostMapping("/revisions/{revisionId}/release")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<CimRevision> releaseRevision(@PathVariable Long revisionId,
                                                       @RequestBody(required = false) CimReleaseRequestDTO request) {
        String comment = request != null ? request.getReleaseComment() : null;
        return ResponseEntity.ok(cimService.releaseRevision(revisionId, comment));
    }

    @PostMapping("/revisions/{revisionId}/import")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<List<CimManufacturingLink>> importToManufacturing(@PathVariable Long revisionId,
                                                                             @RequestBody(required = false) CimImportRequestDTO request) {
        CimImportRequestDTO body = request != null ? request : new CimImportRequestDTO();
        return ResponseEntity.ok(cimService.importToManufacturing(
                revisionId,
                body.getImportMode(),
            body.getConfigurationId(),
                body.getProductDataName(),
                body.getProductBomName(),
                body.getManufacturingBomName()
        ));
    }

    @PostMapping("/revisions/{revisionId}/validate")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<List<CimValidationResult>> validate(@PathVariable Long revisionId) {
        cimService.runValidation(revisionId);
        return ResponseEntity.ok(cimService.getValidationResults(revisionId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<CimObject>> listObjects() {
        return ResponseEntity.ok(cimService.getAllCimObjects());
    }

    @GetMapping("/{cimId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Map<String, Object>> getObject(@PathVariable String cimId) {
        CimObject object = cimService.getCimObject(cimId);
        CimRevision latest = object.getLatestRevision();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cimObject", object);
        response.put("latestRevision", latest);
        response.put("validations", latest != null
                ? cimService.getValidationResults(latest.getRevisionId())
                : List.of());
        response.put("manufacturingLinks", latest != null
                ? cimService.getManufacturingLinks(latest.getRevisionId())
                : List.of());

        return ResponseEntity.ok(response);
    }
}
