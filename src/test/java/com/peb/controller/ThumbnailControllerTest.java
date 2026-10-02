package com.peb.controller;

import com.peb.model.base.Dataset;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.model.ebom.DatasetCategory;
import com.peb.repository.DesignRepository;
import com.peb.service.DatasetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThumbnailControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void servesNewestReadableThumbnailWhenOlderAttachmentIsMissing() throws Exception {
        DesignRepository designRepository = mock(DesignRepository.class);
        DatasetService datasetService = mock(DatasetService.class);
        ThumbnailController controller = new ThumbnailController(designRepository, datasetService);

        Design design = new Design();
        design.setId("DS00000001");
        DesignRevision revision = new DesignRevision();
        revision.setRevisionId(12L);
        design.getRevisions().add(revision);

        Dataset missing = new Dataset();
        missing.setCategory(DatasetCategory.THUMBNAIL);
        missing.setFilePath(tempDir.resolve("missing.png").toString());
        missing.setCreatedDate(LocalDateTime.of(2026, 1, 1, 0, 0));

        Path imagePath = tempDir.resolve("current.png");
        Files.write(imagePath, new byte[] { 1, 2, 3 });
        Dataset current = new Dataset();
        current.setCategory(DatasetCategory.THUMBNAIL);
        current.setFilePath(imagePath.toString());
        current.setCreatedDate(LocalDateTime.of(2026, 2, 1, 0, 0));

        when(designRepository.findById(design.getId())).thenReturn(Optional.of(design));
        when(datasetService.getDatasets(12L, "DESIGN")).thenReturn(List.of(missing, current));

        ResponseEntity<Resource> response = controller.getDesignThumbnail(design.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getFile().toPath().endsWith("current.png"));
    }
}