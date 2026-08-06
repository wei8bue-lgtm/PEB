package com.peb.repository;

import com.peb.model.material.MaterialRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialRevisionRepository extends JpaRepository<MaterialRevision, Long> {
    /** Aufsteigend sortiert (älteste Revision "A" zuerst) */
    List<MaterialRevision> findByMaterialIdOrderByCreatedDateAsc(String materialId);
}
