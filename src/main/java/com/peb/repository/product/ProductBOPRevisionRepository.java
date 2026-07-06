// src/main/java/com/peb/repository/product/ProductBOPRevisionRepository.java
package com.peb.repository.product;

import com.peb.model.product.ProductBOPRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductBOPRevisionRepository
        extends JpaRepository<ProductBOPRevision, Long> {

    List<ProductBOPRevision>
    findByProductBOPIdOrderByRevisionAsc(Long productBopId);
}