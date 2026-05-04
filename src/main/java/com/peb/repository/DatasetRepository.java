package com.peb.repository;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    // Bestehend
    List<Dataset> findByRevisionIdAndRevisionType(
            Long revisionId, String revisionType);

    void deleteByRevisionIdAndRevisionType(
            Long revisionId, String revisionType);

    // Neu: Filterung nach Kategorie
    List<Dataset> findByRevisionIdAndRevisionTypeAndCategory(
            Long revisionId, String revisionType, DatasetCategory category);

    // Neu: Alle Datasets einer Revision (ohne RevisionType-Filter)
    List<Dataset> findByRevisionId(Long revisionId);
}