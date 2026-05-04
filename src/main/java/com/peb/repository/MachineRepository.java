package com.peb.repository;

import com.peb.model.ebom.Machine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MachineRepository extends JpaRepository<Machine, String> {
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(m.id, 3) AS long)), 0) FROM Machine m")
    Long findMaxSequence();
}