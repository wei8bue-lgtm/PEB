// src/main/java/com/peb/repository/product/ManufacturingBOMRevisionRepository.java
package com.peb.repository.product;

import com.peb.model.product.ManufacturingBOMRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ManufacturingBOMRevisionRepository
        extends JpaRepository<ManufacturingBOMRevision, Long> {

    List<ManufacturingBOMRevision>
    findByManufacturingBOMIdOrderByRevisionAsc(Long mfgBomId);
}