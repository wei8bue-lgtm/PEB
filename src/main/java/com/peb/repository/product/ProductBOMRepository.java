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

    Optional<ProductBOM> findByProductDataId(String productDataId);

    @Query("SELECT pb FROM ProductBOM pb " +
           "LEFT JOIN FETCH pb.productData pd " +
           "WHERE pb.id = :id")
    Optional<ProductBOM> findByIdWithProductData(@Param("id") Long id);

    @Query("SELECT pb FROM ProductBOM pb " +
           "WHERE pb.manufacturingBOMs IS EMPTY")
    List<ProductBOM> findUnassigned();
}