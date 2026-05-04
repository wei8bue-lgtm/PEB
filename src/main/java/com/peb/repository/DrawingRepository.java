package com.peb.repository;

import com.peb.model.cad.Drawing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DrawingRepository extends JpaRepository<Drawing, String> {

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(d.id, 3) AS long)), 0) FROM Drawing d")
    Long findMaxSequence();

    List<Drawing> findByNameContainingIgnoreCase(String name);
}