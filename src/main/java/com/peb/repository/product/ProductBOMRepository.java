// src/main/java/com/peb/repository/product/ProductBOMRepository.java
package com.peb.repository.product;

import com.peb.model.product.ProductBOM;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductBOMRepository
        extends JpaRepository<ProductBOM, Long> {

    List<ProductBOM> findByNameContainingIgnoreCase(String name);

    @Query("""
            SELECT pb FROM ProductBOM pb
              LEFT JOIN FETCH pb.productData
             WHERE pb.id = :id
            """)
    Optional<ProductBOM> findByIdWithProductData(
            @Param("id") Long id);

    @Query(value = """
            SELECT pb.* FROM product_bom pb
             WHERE pb.id IS NOT NULL
               AND NOT EXISTS (
                   SELECT 1 FROM product_bom pb2
                    WHERE pb2.product_data_id =
                          pb.product_data_id
                      AND pb2.id != pb.id
               )
               AND pb.product_data_id IS NULL
            """,
           nativeQuery = true)
    List<ProductBOM> findUnassigned();

    // ── Native Query für Revisionskette ───────────────────────────

    @Query(value = """
            SELECT * FROM product_bom
             WHERE master_id = :masterId
                OR id        = :masterId
             ORDER BY revision ASC
            """,
           nativeQuery = true)
    List<ProductBOM> findRevisionChain(
            @Param("masterId") Long masterId);
}