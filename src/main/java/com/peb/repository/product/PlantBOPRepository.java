// src/main/java/com/peb/repository/product/PlantBOPRepository.java
package com.peb.repository.product;

import com.peb.model.product.PlantBOP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlantBOPRepository
        extends JpaRepository<PlantBOP, Long> {

    // ── Native Query für Revisionskette ───────────────────────────

    @Query(value = """
            SELECT * FROM plant_bop
             WHERE master_id = :masterId
                OR id        = :masterId
             ORDER BY revision ASC
            """,
           nativeQuery = true)
    List<PlantBOP> findRevisionChain(
            @Param("masterId") Long masterId);
}