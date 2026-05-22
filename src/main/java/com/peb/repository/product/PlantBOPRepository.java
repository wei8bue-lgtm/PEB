// src/main/java/com/peb/repository/product/PlantBOPRepository.java
package com.peb.repository.product;

import com.peb.model.product.PlantBOP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlantBOPRepository
        extends JpaRepository<PlantBOP, Long> {

    List<PlantBOP> findByNameContainingIgnoreCase(String name);
}