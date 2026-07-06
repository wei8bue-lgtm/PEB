// src/main/java/com/peb/repository/product/ProductBOPRepository.java
package com.peb.repository.product;

import com.peb.model.product.ProductBOP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductBOPRepository
        extends JpaRepository<ProductBOP, Long> {

    List<ProductBOP> findByNameContainingIgnoreCase(String name);

    @Query("""
            SELECT DISTINCT pb FROM ProductBOP pb
              LEFT JOIN FETCH pb.manufacturingBOMs
             WHERE pb.id = :id
            """)
    Optional<ProductBOP> findByIdWithMfgBOMs(
            @Param("id") Long id);

    // ── Native Query für Revisionskette ───────────────────────────

    @Query(value = """
            SELECT * FROM product_bop
             WHERE master_id = :masterId
                OR id        = :masterId
             ORDER BY revision ASC
            """,
           nativeQuery = true)
    List<ProductBOP> findRevisionChain(
            @Param("masterId") Long masterId);
}