package com.peb.repository;

import com.peb.model.ebom.Auxiliary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AuxiliaryRepository extends JpaRepository<Auxiliary, String> {
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(a.id, 3) AS long)), 0) FROM Auxiliary a")
    Long findMaxSequence();
}