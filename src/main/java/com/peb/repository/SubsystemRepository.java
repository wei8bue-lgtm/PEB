package com.peb.repository;

import com.peb.model.ebom.Subsystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SubsystemRepository extends JpaRepository<Subsystem, String> {
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(s.id, 3) AS long)), 0) FROM Subsystem s")
    Long findMaxSequence();
}