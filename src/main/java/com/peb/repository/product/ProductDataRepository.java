// src/main/java/com/peb/repository/product/ProductDataRepository.java
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

    @Query("""
            SELECT pd FROM ProductData pd
             WHERE LOWER(pd.name) LIKE LOWER(
                       CONCAT('%', :q, '%'))
                OR LOWER(pd.id) LIKE LOWER(
                       CONCAT('%', :q, '%'))
             ORDER BY pd.id ASC
            """)
    List<ProductData> searchByQuery(@Param("q") String q);

    // ── Nur PDs ohne zugewiesene BOM ─────────────────────────
    // LEFT JOIN statt direkter NULL-Check – robuster bei
    // bidirektionalen Beziehungen
    @Query("""
            SELECT pd FROM ProductData pd
             LEFT JOIN pd.productBOM bom
             WHERE bom IS NULL
             ORDER BY pd.id ASC
            """)
    List<ProductData> findUnassigned();

    // ── Alle PDs für Dropdown (inkl. bereits zugewiesener) ───
    @Query("""
            SELECT pd FROM ProductData pd
             ORDER BY pd.id ASC
            """)
    List<ProductData> findAllOrderById();

    @Query(value = """
            SELECT MAX(TO_NUMBER(SUBSTR(id, 3)))
              FROM product_data
            """,
           nativeQuery = true)
    Long findMaxSequence();

    @Query(value = """
            SELECT * FROM product_data
             WHERE master_id = :masterId
                OR (master_id IS NULL
                    AND TO_NUMBER(SUBSTR(id, 3)) = :masterId)
             ORDER BY revision ASC
            """,
           nativeQuery = true)
    List<ProductData> findRevisionChain(
            @Param("masterId") Long masterId);
}