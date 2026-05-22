// src/main/java/com/peb/repository/product/PlantBOPNodeRepository.java
package com.peb.repository.product;

import com.peb.model.product.PlantBOPNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlantBOPNodeRepository
        extends JpaRepository<PlantBOPNode, Long> {

    // Root-Knoten (für Sort-Order Berechnung)
    List<PlantBOPNode>
    findByPlantBOPIdAndParentIsNullOrderBySortOrder(
            Long plantBopId);

    // Kinder eines Knotens
    List<PlantBOPNode>
    findByParentIdOrderBySortOrder(Long parentId);

    /**
     * ALLE Knoten einer PlantBOP mit allen Referenzen.
     * Der Baum wird dann im Controller in-memory aufgebaut.
     *
     * Wichtig: parent wird mitgeladen damit wir Root-Knoten
     * filtern können (parent == null).
     */
    @Query("SELECT DISTINCT n FROM PlantBOPNode n " +
           "LEFT JOIN FETCH n.parent " +
           "LEFT JOIN FETCH n.productBOP pb " +
           "LEFT JOIN FETCH pb.manufacturingBOMs " +
           "LEFT JOIN FETCH n.manufacturingBOM " +
           "WHERE n.plantBOP.id = :plantBopId " +
           "ORDER BY n.sortOrder ASC")
    List<PlantBOPNode> findAllByPlantBOPIdWithDetails(
            @Param("plantBopId") Long plantBopId);
}