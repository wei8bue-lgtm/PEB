package com.peb.repository;

import com.peb.model.document.DocumentTemplateRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTemplateRevisionRepository extends JpaRepository<DocumentTemplateRevision, Long> {

    List<DocumentTemplateRevision> findByTemplateId(String templateId);

    Optional<DocumentTemplateRevision> findByTemplateIdAndReleaseStatus(
            String templateId, ReleaseStatus status);

    List<DocumentTemplateRevision> findByTemplateIdAndReleaseStatusNot(
            String templateId, ReleaseStatus status);
}
