package com.peb.repository;

import com.peb.model.material.Material;
import com.peb.model.material.MaterialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, String> {

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(m.id, 3) AS long)), 0) FROM Material m")
    Long findMaxSequence();

    List<Material> findByStatus(MaterialStatus status);

    @Query("SELECT m FROM Material m WHERE " +
           "LOWER(m.name) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(m.materialGroup) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(m.specification) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Material> searchByTerm(@Param("term") String term);
}
