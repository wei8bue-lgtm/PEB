package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.model.cad.Drawing;
import com.peb.model.cad.DrawingRevision;
import com.peb.repository.DrawingRepository;
import com.peb.repository.DrawingRevisionRepository;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing Drawing objects
 */
@Service
@RequiredArgsConstructor
public class DrawingService {

    private final DrawingRepository drawingRepository;
    private final DrawingRevisionRepository drawingRevisionRepository;
    private final IDGeneratorService idGeneratorService;

    @Transactional
    public Drawing createDrawing(String name, String department) {
        Drawing drawing = new Drawing();
        drawing.setId(idGeneratorService.generateId(ObjectType.DRAWING));
        drawing.setName(name);
        drawing.setDepartment(department);

        drawing = drawingRepository.save(drawing);

        // Create initial revision (drawings are created at revision level)
        DrawingRevision revision = new DrawingRevision();
        revision.setDrawing(drawing);
        revision.setRevision("01");
        revision.setReleaseStatus(ReleaseStatus.WORKING);
        revision.syncFromParent();

        drawingRevisionRepository.save(revision);

        return drawing;
    }

    @Transactional(readOnly = true)
    public Drawing getDrawing(String id) {
        return drawingRepository.findById(id)
            .orElseThrow(() -> new ObjectNotFoundException("Drawing not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Drawing> getAllDrawings() {
        return drawingRepository.findAll();
    }

    @Transactional
    public Drawing updateDrawing(String id, String name, String department) {
        Drawing drawing = getDrawing(id);
        drawing.setName(name);
        drawing.setDepartment(department);

        // Sync to latest working revision
        DrawingRevision latestRevision = drawing.getLatestRevision();
        if (latestRevision != null && latestRevision.getReleaseStatus() == ReleaseStatus.WORKING) {
            latestRevision.syncFromParent();
            drawingRevisionRepository.save(latestRevision);
        }

        return drawingRepository.save(drawing);
    }

    @Transactional
    public void deleteDrawing(String id) {
        Drawing drawing = getDrawing(id);
        drawingRepository.delete(drawing);
    }

    @Transactional(readOnly = true)
    public List<Drawing> searchDrawings(String searchTerm) {
        return drawingRepository.findByNameContainingIgnoreCase(searchTerm);
    }
}