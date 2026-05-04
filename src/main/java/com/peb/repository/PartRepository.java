package com.peb.repository;

import com.peb.model.ebom.Part;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PartRepository extends JpaRepository<Part, String> {
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(p.id, 3) AS long)), 0) FROM Part p")
    Long findMaxSequence();
}