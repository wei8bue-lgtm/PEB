package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import com.peb.repository.DatasetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing datasets (file attachments)
 */
@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private static final String UPLOAD_DIR = "data/uploads/";

    @Transactional
    public Dataset createDataset(Long revisionId, String revisionType, 
                                 MultipartFile file, DatasetCategory category) throws IOException {
        // Save file to storage
        String fileName = file.getOriginalFilename();
        String uniqueFileName = UUID.randomUUID() + "_" + fileName;
        Path filePath = Paths.get(UPLOAD_DIR, uniqueFileName);
        
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, file.getBytes());

        Dataset dataset = new Dataset();
        dataset.setRevisionId(revisionId);
        dataset.setRevisionType(revisionType);
        dataset.setFileName(fileName);
        dataset.setFilePath(filePath.toString());
        dataset.setFileSize(file.getSize());
        dataset.setMimeType(file.getContentType());
        dataset.setCategory(category);

        return datasetRepository.save(dataset);
    }

    @Transactional(readOnly = true)
    public List<Dataset> getDatasets(Long revisionId, String revisionType) {
        return datasetRepository.findByRevisionIdAndRevisionType(revisionId, revisionType);
    }

    @Transactional(readOnly = true)
    public Dataset getDataset(Long datasetId) {
        return datasetRepository.findById(datasetId)
                .orElseThrow(() -> new ObjectNotFoundException("Dataset not found: " + datasetId));
    }

    @Transactional
    public void deleteDataset(Long datasetId) throws IOException {
        Dataset dataset = datasetRepository.findById(datasetId)
            .orElseThrow(() -> new ObjectNotFoundException("Dataset not found: " + datasetId));

        // Delete file from storage
        Path filePath = Paths.get(dataset.getFilePath());
        Files.deleteIfExists(filePath);

        datasetRepository.delete(dataset);
    }
}