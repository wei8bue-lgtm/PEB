package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.model.cad.Drawing;
import com.peb.model.cad.DrawingRevision;
import com.peb.repository.DrawingRepository;
import com.peb.repository.DrawingRevisionRepository;
import com.peb.service.search.SearchIndexService;
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
    private final SearchIndexService searchIndexService; // NEU: zentrale Suche

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
        drawing.getRevisions().add(revision); // NEU: haelt In-Memory-Graph konsistent, damit getLatestRevision() sofort greift

        // NEU: zentrale Suche aktualisieren
        searchIndexService.index(ObjectType.DRAWING, drawing);

        return drawing;
    }

    @Transactional(readOnly = true, noRollbackFor = com.peb.exception.ObjectNotFoundException.class)
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

        Drawing saved = drawingRepository.save(drawing);

        // NEU: zentrale Suche aktualisieren
        searchIndexService.index(ObjectType.DRAWING, saved);

        return saved;
    }

    @Transactional
    public void deleteDrawing(String id) {
        Drawing drawing = getDrawing(id);
        drawingRepository.delete(drawing);

        // NEU: aus zentraler Suche entfernen
        searchIndexService.remove(ObjectType.DRAWING, id);
    }

    @Transactional(readOnly = true)
    public List<Drawing> searchDrawings(String searchTerm) {
        return drawingRepository.findByNameContainingIgnoreCase(searchTerm);
    }
}