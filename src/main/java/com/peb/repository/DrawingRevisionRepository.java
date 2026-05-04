package com.peb.repository;

import com.peb.model.cad.DrawingRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrawingRevisionRepository extends JpaRepository<DrawingRevision, Long> {

    List<DrawingRevision> findByDrawingIdOrderByRevisionDesc(String drawingId);

    Optional<DrawingRevision> findFirstByDrawingIdOrderByRevisionDesc(String drawingId);

    Optional<DrawingRevision> findByDrawingIdAndReleaseStatus(String drawingId, ReleaseStatus status);
}