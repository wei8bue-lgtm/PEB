package com.peb.repository;

import com.peb.model.ebom.SystemRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemRevisionRepository extends JpaRepository<SystemRevision, Long> {
    List<SystemRevision> findBySystemIdOrderByRevisionDesc(String systemId);
    Optional<SystemRevision> findFirstBySystemIdOrderByRevisionDesc(String systemId);
    Optional<SystemRevision> findBySystemIdAndReleaseStatus(String systemId, ReleaseStatus status);
    List<SystemRevision> findBySystemIdAndReleaseStatusNot(String systemId, ReleaseStatus status);
}