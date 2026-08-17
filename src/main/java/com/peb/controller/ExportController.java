package com.peb.controller;

import com.peb.service.XMLExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * REST controller for Export operations
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final XMLExportService xmlExportService;

    /**
     * Export eBOM structure to XML (simple format)
     */
    @GetMapping("/ebom/{objectId}/revisions/{revisionId}/xml")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<String> exportToXML(
            @PathVariable String objectId,
            @PathVariable Long revisionId) throws Exception {
        String xml = xmlExportService.exportStructureToXML(objectId, revisionId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", objectId + "_rev" + revisionId + ".xml");
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(xml);
    }

    /**
     * Export eBOM structure to XML (detailed format with all attributes)
     */
    @GetMapping("/ebom/{objectId}/revisions/{revisionId}/xml/detailed")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<String> exportToDetailedXML(
            @PathVariable String objectId,
            @PathVariable Long revisionId) throws Exception {
        String xml = xmlExportService.exportDetailedStructureToXML(objectId, revisionId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", objectId + "_rev" + revisionId + "_detailed.xml");
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(xml);
    }

    /**
     * Export resolved 100% BOM for a specific configuration as XML.
     * All variant-guarded items that do not match the configuration are omitted.
     *
     * GET /api/export/ebom/{objectId}/revisions/{revisionId}/xml/config/{configId}
     */
    @GetMapping("/ebom/{objectId}/revisions/{revisionId}/xml/config/{configId}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<String> exportConfiguredBOM(
            @PathVariable String objectId,
            @PathVariable Long   revisionId,
            @PathVariable Long   configId) throws Exception {
        String xml = xmlExportService.exportConfiguredBOMToXML(objectId, revisionId, configId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "xml", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment",
                objectId + "_rev" + revisionId + "_config" + configId + "_100pct.xml");

        return ResponseEntity.ok()
                .headers(headers)
                .body(xml);
    }
}