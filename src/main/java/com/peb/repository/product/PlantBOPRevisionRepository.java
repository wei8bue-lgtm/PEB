// src/main/java/com/peb/repository/product/PlantBOPRevisionRepository.java
package com.peb.repository.product;

import com.peb.model.product.PlantBOPRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlantBOPRevisionRepository
        extends JpaRepository<PlantBOPRevision, Long> {

    List<PlantBOPRevision>
    findByPlantBOPIdOrderByRevisionAsc(Long plantBopId);
}