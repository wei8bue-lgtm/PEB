package com.peb.repository;

import com.peb.model.cad.DesignRevision;
import com.peb.util.ReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DesignRevisionRepository extends JpaRepository<DesignRevision, Long> {

    List<DesignRevision> findByDesignIdOrderByRevisionDesc(String designId);

    Optional<DesignRevision> findFirstByDesignIdOrderByRevisionDesc(String designId);

    Optional<DesignRevision> findByDesignIdAndReleaseStatus(String designId, ReleaseStatus status);

    List<DesignRevision> findByDesignIdAndReleaseStatusNot(String designId, ReleaseStatus status);
}