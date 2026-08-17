package com.peb.repository.cim;

import com.peb.model.cim.CimRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CimRevisionRepository extends JpaRepository<CimRevision, Long> {

    List<CimRevision> findByCimObjectIdOrderByRevisionDesc(String cimId);

    Optional<CimRevision> findFirstByCimObjectIdOrderByRevisionDesc(String cimId);

    Optional<CimRevision> findFirstByCimObjectIdAndReleaseStatusOrderByRevisionDesc(String cimId, ReleaseStatus releaseStatus);

    List<CimRevision> findByCimObjectIdAndReleaseStatusNot(String cimId, ReleaseStatus releaseStatus);
}
