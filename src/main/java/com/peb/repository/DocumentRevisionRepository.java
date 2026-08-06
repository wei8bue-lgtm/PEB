package com.peb.repository;

import com.peb.model.document.DocumentRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRevisionRepository extends JpaRepository<DocumentRevision, Long> {

    List<DocumentRevision> findByDocumentId(String documentId);

    List<DocumentRevision> findByDocumentIdAndReleaseStatusNot(
            String documentId, ReleaseStatus status);
}
