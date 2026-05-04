package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.repository.DatasetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Service für Dataset-Verwaltung (Dateianhänge)
 * Erweitert um VBScript-kompatible Methoden
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetService {

    private final DatasetRepository datasetRepository;

    @Value("${peb.dataset.upload-dir:data/uploads/}")
    private String uploadDir;

    // ─────────────────────────────────────────────
    // BESTEHENDE METHODEN (ergänzt/korrigiert)
    // ─────────────────────────────────────────────

    @Transactional
    public Dataset createDataset(Long revisionId, String revisionType,
                                 MultipartFile file, DatasetCategory category) throws IOException {

        validateFile(file);

        String originalFileName = file.getOriginalFilename();
        String uniqueFileName   = UUID.randomUUID() + "_" + originalFileName;
        Path   filePath         = Paths.get(uploadDir, uniqueFileName);

        Files.createDirectories(filePath.getParent());
        Files.write(filePath, file.getBytes());

        Dataset dataset = new Dataset();
        dataset.setRevisionId(revisionId);
        dataset.setRevisionType(revisionType);
        dataset.setFileName(originalFileName);
        dataset.setFilePath(filePath.toString());
        dataset.setFileSize(file.getSize());
        dataset.setMimeType(file.getContentType());
        dataset.setCategory(category);

        Dataset saved = datasetRepository.save(dataset);
        log.info("Dataset erstellt: ID={}, Datei={}, RevisionId={}",
                 saved.getDatasetId(), originalFileName, revisionId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Dataset> getDatasets(Long revisionId, String revisionType) {
        return datasetRepository.findByRevisionIdAndRevisionType(revisionId, revisionType);
    }

    @Transactional(readOnly = true)
    public Dataset getDataset(Long datasetId) {
        return datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Dataset nicht gefunden: " + datasetId));
    }

    /**
     * Lädt Dataset als Spring Resource (für DatasetController)
     * War bisher nicht implementiert → behebt den Kompilierungsfehler
     */
    @Transactional(readOnly = true)
    public Resource loadDatasetAsResource(Long datasetId) throws MalformedURLException {
        Dataset dataset = getDataset(datasetId);
        Path    path    = Paths.get(dataset.getFilePath());

        Resource resource = new UrlResource(path.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new ObjectNotFoundException(
                    "Datei nicht lesbar: " + dataset.getFileName());
        }
        return resource;
    }

    @Transactional
    public void deleteDataset(Long datasetId) throws IOException {
        Dataset dataset = getDataset(datasetId);

        Path filePath = Paths.get(dataset.getFilePath());
        Files.deleteIfExists(filePath);

        datasetRepository.delete(dataset);
        log.info("Dataset gelöscht: ID={}, Datei={}", datasetId, dataset.getFileName());
    }

    // ─────────────────────────────────────────────
    // NEUE METHODEN für VBScript-Integration
    // ─────────────────────────────────────────────

    /**
     * Datasets nach Kategorie filtern
     */
    @Transactional(readOnly = true)
    public List<Dataset> getDatasetsByCategory(Long revisionId,
                                               String revisionType,
                                               DatasetCategory category) {
        return datasetRepository
                .findByRevisionIdAndRevisionTypeAndCategory(revisionId, revisionType, category);
    }

    /**
     * Dataset-Kategorie nachträglich ändern
     */
    @Transactional
    public Dataset updateDatasetCategory(Long datasetId, DatasetCategory newCategory) {
        Dataset dataset = getDataset(datasetId);
        dataset.setCategory(newCategory);
        Dataset saved = datasetRepository.save(dataset);
        log.info("Dataset-Kategorie geändert: ID={}, Kategorie={}", datasetId, newCategory);
        return saved;
    }

    /**
     * Prüft ob eine Datei mit diesem Namen bereits existiert
     */
    @Transactional(readOnly = true)
    public boolean datasetExistsByFileName(Long revisionId,
                                           String revisionType,
                                           String fileName) {
        return datasetRepository
                .findByRevisionIdAndRevisionType(revisionId, revisionType)
                .stream()
                .anyMatch(d -> d.getFileName().equalsIgnoreCase(fileName));
    }

    /**
     * Validierung der hochgeladenen Datei
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Datei darf nicht leer sein");
        }
        // Max 100 MB
        if (file.getSize() > 100L * 1024 * 1024) {
            throw new IllegalArgumentException("Datei überschreitet 100 MB Limit");
        }
    }
}