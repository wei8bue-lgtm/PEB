package com.peb.controller;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;

/**
 * REST controller for Dataset operations
 */
@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<Dataset> uploadDataset(
            @RequestParam Long revisionId,
            @RequestParam String revisionType,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) DatasetCategory category) throws IOException {
        Dataset dataset = datasetService.createDataset(revisionId, revisionType, file, category);
        return ResponseEntity.ok(dataset);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<Dataset>> getDatasets(
            @RequestParam Long revisionId,
            @RequestParam String revisionType) {
        List<Dataset> datasets = datasetService.getDatasets(revisionId, revisionType);
        return ResponseEntity.ok(datasets);
    }

    @GetMapping("/{datasetId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Dataset> getDataset(@PathVariable Long datasetId) {
        Dataset dataset = datasetService.getDataset(datasetId);
        return ResponseEntity.ok(dataset);
    }

    @GetMapping("/{datasetId}/download")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Resource> downloadDataset(
            @PathVariable Long datasetId) throws MalformedURLException {
        Resource resource = datasetService.loadDatasetAsResource(datasetId);
        Dataset dataset   = datasetService.getDataset(datasetId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        dataset.getMimeType() != null
                                ? dataset.getMimeType()
                                : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + dataset.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{datasetId}")
    @PreAuthorize("hasAuthority('DELETE')")
    public ResponseEntity<Void> deleteDataset(
            @PathVariable Long datasetId) throws IOException {
        datasetService.deleteDataset(datasetId);
        return ResponseEntity.noContent().build();
    }
}