package com.peb.repository;

import com.peb.model.ebom.EBOMModuleRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EBOMModuleRevisionRepository extends JpaRepository<EBOMModuleRevision, Long> {
    List<EBOMModuleRevision> findByModuleIdOrderByRevisionDesc(String moduleId);
    Optional<EBOMModuleRevision> findFirstByModuleIdOrderByRevisionDesc(String moduleId);
    Optional<EBOMModuleRevision> findByModuleIdAndReleaseStatus(String moduleId, ReleaseStatus status);
    List<EBOMModuleRevision> findByModuleIdAndReleaseStatusNot(String moduleId, ReleaseStatus status);
}