// src/main/java/com/peb/service/DrawingWorkflowService.java
package com.peb.service;

import com.peb.exception.ObjectNotFoundException;
import com.peb.model.cad.Drawing;
import com.peb.model.cad.DrawingRevision;
import com.peb.repository.DrawingRepository;
import com.peb.repository.DrawingRevisionRepository;
import com.peb.util.ReleaseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Workflow transitions for Drawing revisions:
 * WORKING → RELEASED, older revisions → OBSOLETE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DrawingWorkflowService {

    private final DrawingRepository         drawingRepository;
    private final DrawingRevisionRepository drawingRevisionRepository;

    /**
     * Releases the specified DrawingRevision.
     * All previously RELEASED revisions of the same Drawing become OBSOLETE.
     *
     * @param drawingId   Drawing master ID
     * @param revisionId  PK of the DrawingRevision to release
     * @param releasedBy  username from Spring Security
     */
    @Transactional
    public void releaseDrawingRevision(String drawingId,
                                       Long   revisionId,
                                       String releasedBy) {

        Drawing drawing = drawingRepository.findById(drawingId)
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Drawing not found: " + drawingId));

        // Find the target revision inside the drawing
        DrawingRevision target = drawing.getRevisions().stream()
                .filter(r -> r.getRevisionId().equals(revisionId))
                .findFirst()
                .orElseThrow(() -> new ObjectNotFoundException(
                        "DrawingRevision not found: " + revisionId));

        if (target.getReleaseStatus() != ReleaseStatus.WORKING) {
            throw new IllegalStateException(
                    "Only WORKING revisions can be released. "
                    + "Current status: " + target.getReleaseStatus());
        }

        // Mark older released revisions as OBSOLETE
        List<DrawingRevision> toObsolete = drawing.getRevisions().stream()
                .filter(r -> r.getReleaseStatus() == ReleaseStatus.RELEASED
                          && !r.getRevisionId().equals(revisionId))
                .toList();

        toObsolete.forEach(r -> {
            r.setReleaseStatus(ReleaseStatus.OBSOLETE);
            drawingRevisionRepository.save(r);
            log.info("DrawingRevision[{}] rev={} → OBSOLETE", r.getRevisionId(), r.getRevision());
        });

        // Release the target revision
        target.setReleaseStatus(ReleaseStatus.RELEASED);
        drawingRevisionRepository.save(target);

        log.info("DrawingRevision[{}] rev={} RELEASED by {}",
                revisionId, target.getRevision(), releasedBy);
    }
}