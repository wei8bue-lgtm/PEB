package com.peb.repository;

import com.peb.model.base.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    List<Dataset> findByRevisionIdAndRevisionType(Long revisionId, String revisionType);

    void deleteByRevisionIdAndRevisionType(Long revisionId, String revisionType);
}