// src/main/java/com/peb/repository/product/ProductDataRevisionRepository.java
package com.peb.repository.product;

import com.peb.model.product.ProductDataRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductDataRevisionRepository
        extends JpaRepository<ProductDataRevision, Long> {

    List<ProductDataRevision>
    findByProductDataIdOrderByRevisionAsc(String productDataId);
}