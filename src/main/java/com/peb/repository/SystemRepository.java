package com.peb.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemRepository extends JpaRepository<com.peb.model.ebom.System, String> {
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(s.id, 3) AS long)), 0) FROM System s")
    Long findMaxSequence();
}