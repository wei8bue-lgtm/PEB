package com.peb.controller;

import com.peb.model.base.Dataset;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.model.ebom.DatasetCategory;
import com.peb.repository.DesignRepository;
import com.peb.service.DatasetService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Controller für Thumbnail-Anzeige von Design-Objekten.
 * Liefert das Thumbnail-Bild (jpeg/jpg/bmp/png) der neuesten Revision.
 *
 * Endpunkt: GET /designs/{id}/thumbnail
 */
@RestController
@RequiredArgsConstructor
public class ThumbnailController {

    private final DesignRepository designRepository;
    private final DatasetService   datasetService;

    /**
     * Liefert das Thumbnail-Bild der neuesten Revision eines Designs.
     *
     * @param id Design-ID (z.B. "DS00000001")
     * @return Bild als Resource (image/jpeg, image/png, image/bmp)
     */
    @GetMapping("/designs/{id}/thumbnail")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Resource> getDesignThumbnail(@PathVariable String id) {

        // 1. Design laden
        Design design = designRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Design nicht gefunden: " + id));

        // 2. Neueste Revision ermitteln
        DesignRevision revision = design.getLatestRevision();
        if (revision == null) {
            return ResponseEntity.notFound().build();
        }

        // 3. Datasets der Revision laden und Thumbnail suchen
        //    DatasetService.getDatasets() erwartet revisionId + revisionType
        Dataset thumbnailDataset = datasetService
                .getDatasets(revision.getRevisionId(), "DESIGN")
                .stream()
                .filter(ds -> ds.getCategory() == DatasetCategory.THUMBNAIL)
                .findFirst()
                .orElse(null);

        if (thumbnailDataset == null || thumbnailDataset.getFilePath() == null) {
            // Kein Thumbnail vorhanden → 404 (onerror im HTML greift)
            return ResponseEntity.notFound().build();
        }

        // 4. Datei als Resource laden
        try {
            Path     filePath = Paths.get(thumbnailDataset.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            // 5. Content-Type anhand Dateiendung bestimmen
            String mediaType = determineImageMediaType(
                    filePath.getFileName().toString().toLowerCase());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mediaType))
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                    .body(resource);

        } catch (MalformedURLException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Thumbnail-Datei konnte nicht geladen werden");
        }
    }

    /**
     * Bestimmt den MIME-Type anhand der Dateiendung.
     * Unterstützte Formate: jpeg, jpg, bmp, png
     */
    private String determineImageMediaType(String filename) {
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;          // image/jpeg
        } else if (filename.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;           // image/png
        } else if (filename.endsWith(".bmp")) {
            return "image/bmp";
        }
        return MediaType.IMAGE_JPEG_VALUE;              // Fallback
    }
}