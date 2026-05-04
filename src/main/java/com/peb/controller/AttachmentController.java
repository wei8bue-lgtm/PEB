package com.peb.controller;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * REST controller for file attachments (datasets)
 */
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final DatasetService datasetService;

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('WRITE')")
    public ResponseEntity<Dataset> uploadFile(
            @RequestParam Long revisionId,
            @RequestParam String revisionType,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) DatasetCategory category) throws IOException {
        
        Dataset dataset = datasetService.createDataset(revisionId, revisionType, file, category);
        return ResponseEntity.ok(dataset);
    }

    @GetMapping("/{revisionId}/{revisionType}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<List<Dataset>> getAttachments(
            @PathVariable Long revisionId,
            @PathVariable String revisionType) {
        
        List<Dataset> datasets = datasetService.getDatasets(revisionId, revisionType);
        return ResponseEntity.ok(datasets);
    }

    @GetMapping("/download/{datasetId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long datasetId) throws IOException {
        Dataset dataset = datasetService.getDataset(datasetId);
        
        Path filePath = Paths.get(dataset.getFilePath());
        byte[] fileContent = Files.readAllBytes(filePath);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(dataset.getMimeType()));
        headers.setContentDispositionFormData("attachment", dataset.getFileName());
        headers.setContentLength(fileContent.length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(fileContent);
    }

    @DeleteMapping("/{datasetId}")
    @PreAuthorize("hasAuthority('DELETE')")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long datasetId) throws IOException {
        datasetService.deleteDataset(datasetId);
        return ResponseEntity.noContent().build();
    }
}