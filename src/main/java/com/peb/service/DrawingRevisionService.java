// src/main/java/com/peb/service/DrawingRevisionService.java
package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.model.cad.Drawing;
import com.peb.model.cad.DrawingRevision;
import com.peb.repository.DrawingRepository;
import com.peb.repository.DrawingRevisionRepository;
import com.peb.util.ObjectType;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Drawing revision lifecycle:
 * Revise, Save As (Copy), Release
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DrawingRevisionService {

    private final DrawingRepository         drawingRepository;
    private final DrawingRevisionRepository drawingRevisionRepository;
    private final IDGeneratorService        idGeneratorService;

    // ── Revise ────────────────────────────────────────────────────────────────

    /**
     * Creates a new WORKING revision for an existing Drawing.
     * Only allowed when the latest revision is RELEASED.
     *
     * @param drawingId  the Drawing master ID (e.g. "DW00000001")
     * @param createdBy  username from Spring Security
     * @return           the newly created DrawingRevision
     */
    @Transactional
    public DrawingRevision reviseDrawing(String drawingId, String createdBy) {

        Drawing drawing = drawingRepository.findById(drawingId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Drawing not found: " + drawingId));

        DrawingRevision latest = drawing.getLatestRevision();

        if (latest == null) {
            throw new IllegalStateException(
                    "Drawing has no revisions – cannot revise.");
        }

        if (latest.getReleaseStatus() != ReleaseStatus.RELEASED) {
            throw new IllegalStateException(
                    "Only RELEASED revisions can be revised. "
                    + "Current status: " + latest.getReleaseStatus());
        }

        // Calculate next revision label (01 → 02 → 03 …)
        String nextRevLabel = nextRevisionLabel(latest.getRevision());

        DrawingRevision newRevision = new DrawingRevision();
        newRevision.setDrawing(drawing);
        newRevision.setRevision(nextRevLabel);
        newRevision.setReleaseStatus(ReleaseStatus.WORKING);
        newRevision.setCreatedBy(createdBy);
        newRevision.syncFromParent();

        DrawingRevision saved = drawingRevisionRepository.save(newRevision);

        log.info("Drawing[{}] revised: {} → {} by {}",
                drawingId, latest.getRevision(), nextRevLabel, createdBy);

        return saved;
    }

    // ── Save As ───────────────────────────────────────────────────────────────

    /**
     * Creates a complete copy (new Drawing master + initial WORKING revision).
     *
     * @param sourceDrawingId  source Drawing ID to copy from
     * @param newName          name for the new Drawing
     * @param createdBy        username from Spring Security
     * @return                 the newly created Drawing
     */
    @Transactional
    public Drawing saveAsDrawing(String sourceDrawingId,
                                 String newName,
                                 String createdBy) {

        Drawing source = drawingRepository.findById(sourceDrawingId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Source Drawing not found: " + sourceDrawingId));

        // ── New Drawing master ────────────────────────────────────────────────
        Drawing copy = new Drawing();
        copy.setId(idGeneratorService.generateId(ObjectType.DRAWING));
        copy.setName(newName != null && !newName.isBlank()
                ? newName : source.getName() + " (Copy)");
        copy.setDepartment(source.getDepartment());

        copy = drawingRepository.save(copy);

        // ── Initial WORKING revision ──────────────────────────────────────────
        DrawingRevision initRevision = new DrawingRevision();
        initRevision.setDrawing(copy);
        initRevision.setRevision("01");
        initRevision.setReleaseStatus(ReleaseStatus.WORKING);
        initRevision.setCreatedBy(createdBy);
        initRevision.syncFromParent();

        drawingRevisionRepository.save(initRevision);

        log.info("Drawing[{}] copied → Drawing[{}] ('{}') by {}",
                sourceDrawingId, copy.getId(), copy.getName(), createdBy);

        return copy;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Increments a zero-padded numeric revision label.
     * "01" → "02", "09" → "10", "99" → "100"
     */
    private String nextRevisionLabel(String current) {
        try {
            int next     = Integer.parseInt(current.trim()) + 1;
            int padWidth = Math.max(current.trim().length(), 2);
            return String.format("%0" + padWidth + "d", next);
        } catch (NumberFormatException e) {
            // Fallback for non-numeric labels (A, B, C …)
            return current + "_1";
        }
    }
}