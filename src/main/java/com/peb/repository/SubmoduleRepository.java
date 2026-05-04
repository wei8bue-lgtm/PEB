package com.peb.repository;

import com.peb.model.ebom.Submodule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmoduleRepository extends JpaRepository<Submodule, String> {
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(s.id, 3) AS long)), 0) FROM Submodule s")
    Long findMaxSequence();
}