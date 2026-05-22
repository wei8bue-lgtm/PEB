// src/main/java/com/peb/repository/product/ManufacturingBOMRepository.java
package com.peb.repository.product;

import com.peb.model.product.ManufacturingBOM;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ManufacturingBOMRepository
        extends JpaRepository<ManufacturingBOM, Long> {

    List<ManufacturingBOM> findByNameContainingIgnoreCase(
            String name);

    // ── Für ManufacturingBOMController (Detail-Seite) ─────────────
    /**
     * Lädt MfgBOM mit ProductBOMs UND Operations.
     * Wird im Controller für die Detail-Ansicht verwendet.
     * Zwei separate Queries vermeiden MultipleBagFetchException.
     */
    @Query("SELECT DISTINCT m FROM ManufacturingBOM m " +
           "LEFT JOIN FETCH m.productBOMs " +
           "WHERE m.id = :id")
    Optional<ManufacturingBOM> findByIdWithProductBOMs(
            @Param("id") Long id);

    @Query("SELECT DISTINCT m FROM ManufacturingBOM m " +
           "LEFT JOIN FETCH m.operations " +
           "WHERE m.id = :id")
    Optional<ManufacturingBOM> findByIdWithOperations(
            @Param("id") Long id);

    // ── Für LineBalancingService & OperationPdfService ────────────
    /**
     * Alias für findByIdWithOperations –
     * wird von LineBalancingService und OperationPdfService
     * verwendet. Lädt nur Operations (kein MultipleBag-Problem).
     */
    default Optional<ManufacturingBOM> findByIdWithDetails(Long id) {
        return findByIdWithOperations(id);
    }

    // ── Für Listen-Ansicht ────────────────────────────────────────
    @Query("SELECT m FROM ManufacturingBOM m WHERE m.id = :id")
    Optional<ManufacturingBOM> findByIdOnly(
            @Param("id") Long id);
}