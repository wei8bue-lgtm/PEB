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

    /**
     * Lädt ProductBOP mit ManufacturingBOMs per JOIN FETCH.
     * Nur EINE Collection per Query → kein MultipleBagFetch!
     */
    @Query("SELECT pb FROM ProductBOP pb " +
           "LEFT JOIN FETCH pb.manufacturingBOMs " +
           "WHERE pb.id = :id")
    Optional<ProductBOP> findByIdWithMfgBOMs(@Param("id") Long id);
}