package com.peb.service;

import com.peb.model.base.Dataset;          // ← Import fehlt
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;

@Service
@Slf4j
public class CachedConverterService {

    private final LibreOfficeConverterService converterService;
    private final DatasetService              datasetService;

    public CachedConverterService(
            LibreOfficeConverterService converterService,
            DatasetService              datasetService) {
        this.converterService = converterService;
        this.datasetService   = datasetService;
    }

    /**
     * Ergebnis wird pro datasetId gecacht.
     * Cache wird invalidiert wenn Dataset aktualisiert wird.
     *
     * @param datasetId Long (nicht String!)
     */
    @Cacheable(value = "pdfConversions", key = "#datasetId")
    public byte[] getAsPdf(Long datasetId) throws Exception {  // ← Long statt String

        log.info("PDF-Konvertierung für Dataset {}", datasetId);

        // ✅ getDataset() statt findById()
        Dataset dataset = datasetService.getDataset(datasetId);

        // ✅ Datei direkt über Pfad laden statt loadContent()
        byte[] content = Files.readAllBytes(
                Paths.get(dataset.getFilePath()));

        return converterService.convertExcelToPdf(
                content,
                dataset.getFileName()
        );
    }
}