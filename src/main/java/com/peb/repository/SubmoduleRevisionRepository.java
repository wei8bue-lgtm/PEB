package com.peb.repository;

import com.peb.model.ebom.SubmoduleRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmoduleRevisionRepository extends JpaRepository<SubmoduleRevision, Long> {
    List<SubmoduleRevision> findBySubmoduleIdOrderByRevisionDesc(String submoduleId);
    Optional<SubmoduleRevision> findFirstBySubmoduleIdOrderByRevisionDesc(String submoduleId);
    Optional<SubmoduleRevision> findBySubmoduleIdAndReleaseStatus(String submoduleId, ReleaseStatus status);
    List<SubmoduleRevision> findBySubmoduleIdAndReleaseStatusNot(String submoduleId, ReleaseStatus status);
}