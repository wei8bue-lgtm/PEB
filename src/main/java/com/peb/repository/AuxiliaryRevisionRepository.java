package com.peb.repository;

import com.peb.model.ebom.AuxiliaryRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuxiliaryRevisionRepository extends JpaRepository<AuxiliaryRevision, Long> {
    List<AuxiliaryRevision> findByAuxiliaryIdOrderByRevisionDesc(String auxiliaryId);
    Optional<AuxiliaryRevision> findFirstByAuxiliaryIdOrderByRevisionDesc(String auxiliaryId);
    Optional<AuxiliaryRevision> findByAuxiliaryIdAndReleaseStatus(String auxiliaryId, ReleaseStatus status);
    List<AuxiliaryRevision> findByAuxiliaryIdAndReleaseStatusNot(String auxiliaryId, ReleaseStatus status);
}