// src/main/java/com/peb/repository/product/ManufacturingOperationRepository.java
package com.peb.repository.product;

import com.peb.model.product.ManufacturingOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ManufacturingOperationRepository
        extends JpaRepository<ManufacturingOperation, Long> {

    List<ManufacturingOperation>
    findByManufacturingBOMIdOrderBySequenceNumber(Long mfgBomId);
}