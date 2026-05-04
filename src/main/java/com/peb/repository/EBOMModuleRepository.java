package com.peb.repository;

import com.peb.model.ebom.EBOMModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface EBOMModuleRepository extends JpaRepository<EBOMModule, String> {
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(m.id, 3) AS long)), 0) FROM EBOMModule m")
    Long findMaxSequence();
}