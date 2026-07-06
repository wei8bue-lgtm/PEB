// src/main/java/com/peb/repository/product/ProductBOMRevisionRepository.java
package com.peb.repository.product;

import com.peb.model.product.ProductBOMRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductBOMRevisionRepository
        extends JpaRepository<ProductBOMRevision, Long> {

    List<ProductBOMRevision>
    findByProductBOMIdOrderByRevisionAsc(Long productBomId);
}