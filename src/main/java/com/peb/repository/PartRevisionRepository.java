package com.peb.repository;

import com.peb.model.ebom.PartRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartRevisionRepository extends JpaRepository<PartRevision, Long> {
    List<PartRevision> findByPartIdOrderByRevisionDesc(String partId);
    Optional<PartRevision> findFirstByPartIdOrderByRevisionDesc(String partId);
    Optional<PartRevision> findByPartIdAndReleaseStatus(String partId, ReleaseStatus status);
    List<PartRevision> findByPartIdAndReleaseStatusNot(String partId, ReleaseStatus status);
}