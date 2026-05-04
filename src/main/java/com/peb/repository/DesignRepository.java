package com.peb.repository;

import com.peb.model.cad.Design;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DesignRepository extends JpaRepository<Design, String> {

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(d.id, 3) AS long)), 0) FROM Design d")
    Long findMaxSequence();

    List<Design> findByNameContainingIgnoreCase(String name);

    List<Design> findByDepartmentContainingIgnoreCase(String department);
}