package com.peb.service;

import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Service for managing thumbnails
 */
@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private static final String THUMBNAIL_DIR = "data/thumbnails/";
    private static final int THUMBNAIL_WIDTH = 200;
    private static final int THUMBNAIL_HEIGHT = 200;

    public String createThumbnail(MultipartFile imageFile) throws IOException {
        String uniqueFileName = UUID.randomUUID() + "_thumb.jpg";
        Path thumbnailPath = Paths.get(THUMBNAIL_DIR, uniqueFileName);
        
        Files.createDirectories(thumbnailPath.getParent());

        Thumbnails.of(imageFile.getInputStream())
            .size(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
            .outputFormat("jpg")
            .toFile(thumbnailPath.toFile());

        return thumbnailPath.toString();
    }

    public void deleteThumbnail(String thumbnailPath) throws IOException {
        if (thumbnailPath != null) {
            Path path = Paths.get(thumbnailPath);
            Files.deleteIfExists(path);
        }
    }
}