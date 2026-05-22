// DATEI: src/main/java/com/peb/repository/product/ProductDataRepository.java
package com.peb.repository.product;

import com.peb.model.product.ProductData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductDataRepository
        extends JpaRepository<ProductData, String> {

    // ── ID-Generator (wird von IDGeneratorService genutzt) ───────
    /**
     * Gibt die höchste Sequenznummer zurück.
     * "PD00000042" → 42L
     * Wird von IDGeneratorService.getNextSequence() aufgerufen.
     */
    @Query(value =
        "SELECT MAX(TO_NUMBER(SUBSTR(id, 3))) " +
        "FROM product_data " +
        "WHERE REGEXP_LIKE(id, '^PD[0-9]{8}$')",
        nativeQuery = true)
    Long findMaxSequence();

    // ── Suche ─────────────────────────────────────────────────────
    @Query("SELECT p FROM ProductData p WHERE " +
           "UPPER(p.name)        LIKE UPPER(CONCAT('%',:q,'%')) OR " +
           "UPPER(p.id)          LIKE UPPER(CONCAT('%',:q,'%')) OR " +
           "UPPER(p.department)  LIKE UPPER(CONCAT('%',:q,'%')) OR " +
           "UPPER(p.productType) LIKE UPPER(CONCAT('%',:q,'%'))")
    List<ProductData> searchByQuery(@Param("q") String query);

    // ── Hilfsmethoden ─────────────────────────────────────────────
    @Query("SELECT p FROM ProductData p " +
           "WHERE p.productBOM IS NULL " +
           "ORDER BY p.id ASC")
    List<ProductData> findUnassigned();

    List<ProductData> findByDepartmentOrderById(String department);
}