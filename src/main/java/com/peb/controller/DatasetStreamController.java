package com.peb.controller;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.service.DatasetService;
import com.peb.service.LibreOfficeConverterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Liefert Dataset-Dateien als Stream für den Embedded Viewer.
 *
 * GET  /datasets/{datasetId}/stream  → PDF/Excel inline streamen
 * HEAD /datasets/{datasetId}/as-pdf  → Verfügbarkeit prüfen (JS-Check)
 * GET  /datasets/{datasetId}/as-pdf  → Excel → PDF konvertieren (LibreOffice)
 */
@RestController
@RequestMapping("/datasets")
@Slf4j
public class DatasetStreamController {

    private final DatasetService              datasetService;
    private final LibreOfficeConverterService converterService;

    public DatasetStreamController(
            DatasetService              datasetService,
            LibreOfficeConverterService converterService) {
        this.datasetService   = datasetService;
        this.converterService = converterService;
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /datasets/{datasetId}/stream
    // PDF  → inline im Browser via <iframe>
    // Excel → inline als Download-Angebot
    // ══════════════════════════════════════════════════════════════════
	@GetMapping("/{datasetId}/stream")
	@PreAuthorize("hasAuthority('READ')")
	public ResponseEntity<Resource> streamDataset(
			@PathVariable Long datasetId) {
		try {
			Dataset  dataset  = datasetService.getDataset(datasetId);
			String   mime     = resolveMimeType(dataset.getCategory());
			Resource resource = loadResource(dataset);

			log.info("Stream: datasetId={}, file={}, mime={}",
					datasetId, dataset.getFileName(), mime);

			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType(mime))
					.header(HttpHeaders.CONTENT_DISPOSITION,
							"inline; filename=\""
							+ dataset.getFileName() + "\"")
					// ✅ NEU: Browser darf PDF anzeigen
					.header("X-Frame-Options", "SAMEORIGIN")
					.header("Content-Security-Policy",
							"default-src 'self'")
					.body(resource);

		} catch (Exception e) {
			log.error("Stream fehlgeschlagen für Dataset {}: {}",
					datasetId, e.getMessage());
			return ResponseEntity.notFound().build();
		}
	}

    // ══════════════════════════════════════════════════════════════════
    // HEAD /datasets/{datasetId}/as-pdf
    // Prüft ob Konvertierung verfügbar ist.
    // Wird von JS requestExcelAsPdf() aufgerufen.
    // ✅ Wirft NIE eine Exception — gibt immer ResponseEntity zurück!
    // ══════════════════════════════════════════════════════════════════
    @RequestMapping(
            value  = "/{datasetId}/as-pdf",
            method = RequestMethod.HEAD)
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Void> checkAsPdf(@PathVariable Long datasetId) {
        try {
            Dataset dataset = datasetService.getDataset(datasetId);

            // Nur Excel-Kategorien erlauben
            if (dataset.getCategory() != DatasetCategory.EXCEL_TABLE) {
                log.warn("HEAD as-pdf: Dataset {} ist kein EXCEL_TABLE",
                        datasetId);
                return ResponseEntity.badRequest().build();
            }

            // LibreOffice erreichbar?
            converterService.checkAvailability();

            log.info("HEAD as-pdf OK: datasetId={}", datasetId);
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            // ✅ Immer HTTP-Response — nie Exception propagieren!
            // "Verbindung verweigert" entsteht wenn hier eine Exception
            // unkontrolliert nach oben geht!
            log.warn("HEAD as-pdf nicht verfügbar für {}: {}",
                    datasetId, e.getMessage());
            return ResponseEntity.status(503).build();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // GET /datasets/{datasetId}/as-pdf
    // Excel → PDF via LibreOfficeConverterService
    // ══════════════════════════════════════════════════════════════════
    @GetMapping("/{datasetId}/as-pdf")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Resource> streamAsPdf(
            @PathVariable Long datasetId) {
        try {
            Dataset dataset = datasetService.getDataset(datasetId);

            // Nur Excel erlauben
            if (dataset.getCategory() != DatasetCategory.EXCEL_TABLE) {
                log.warn("as-pdf: Dataset {} ist kein EXCEL_TABLE",
                        datasetId);
                return ResponseEntity.badRequest().build();
            }

            // Datei laden
            byte[] excelContent = loadBytes(dataset);

            // LibreOffice Konvertierung
            byte[] pdfContent = converterService.convertExcelToPdf(
                    excelContent,
                    dataset.getFileName()
            );

            String pdfName = dataset.getFileName()
                    .replaceAll("(?i)\\.(xlsx|xls|ods|csv)$", ".pdf");

            log.info("as-pdf OK: {} → {} ({} KB)",
                    dataset.getFileName(), pdfName,
                    pdfContent.length / 1024);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + pdfName + "\"")
                    .contentLength(pdfContent.length)
                    .body(new ByteArrayResource(pdfContent));

        } catch (Exception e) {
            log.error("as-pdf fehlgeschlagen für Dataset {}: {}",
                    datasetId, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Hilfsmethoden
    // ══════════════════════════════════════════════════════════════════

    private Resource loadResource(Dataset dataset)
            throws MalformedURLException {
        Path     path     = Paths.get(dataset.getFilePath());
        Resource resource = new UrlResource(path.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalStateException(
                    "Datei nicht lesbar: " + dataset.getFilePath());
        }
        return resource;
    }

    private byte[] loadBytes(Dataset dataset) throws Exception {
        Path path = Paths.get(dataset.getFilePath());
        return java.nio.file.Files.readAllBytes(path);
    }

    private String resolveMimeType(DatasetCategory category) {
        if (category == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return switch (category) {
            case PDF_DOCUMENT, PDF_DRAWING ->
                    MediaType.APPLICATION_PDF_VALUE;
            case EXCEL_TABLE ->
                    "application/vnd.openxmlformats-officedocument"
                    + ".spreadsheetml.sheet";
            default ->
                    MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }
}