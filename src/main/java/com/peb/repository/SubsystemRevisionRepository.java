package com.peb.repository;

import com.peb.model.ebom.SubsystemRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubsystemRevisionRepository extends JpaRepository<SubsystemRevision, Long> {
    List<SubsystemRevision> findBySubsystemIdOrderByRevisionDesc(String subsystemId);
    Optional<SubsystemRevision> findFirstBySubsystemIdOrderByRevisionDesc(String subsystemId);
    Optional<SubsystemRevision> findBySubsystemIdAndReleaseStatus(String subsystemId, ReleaseStatus status);
    List<SubsystemRevision> findBySubsystemIdAndReleaseStatusNot(String subsystemId, ReleaseStatus status);
}