// src/main/java/com/peb/repository/product/ManufacturingBOMRepository.java
package com.peb.repository.product;

import com.peb.model.product.ManufacturingBOM;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ManufacturingBOMRepository
        extends JpaRepository<ManufacturingBOM, Long> {

    List<ManufacturingBOM> findByNameContainingIgnoreCase(
            String name);

    @Query("""
            SELECT m FROM ManufacturingBOM m
             WHERE m.id = :id
            """)
    Optional<ManufacturingBOM> findByIdOnly(
            @Param("id") Long id);

    @Query("""
            SELECT DISTINCT m FROM ManufacturingBOM m
              LEFT JOIN FETCH m.productBOMs pb
             WHERE m.id = :id
            """)
    Optional<ManufacturingBOM> findByIdWithProductBOMs(
            @Param("id") Long id);

	@Query("""
			SELECT DISTINCT m FROM ManufacturingBOM m
			  LEFT JOIN FETCH m.operations
			 WHERE m.id = :id
			""")
	Optional<ManufacturingBOM> findByIdWithDetails(
			@Param("id") Long id);

    // ── Native Query statt JPQL ───────────────────────────────────
    // GRUND: masterId ist ein Feld aus @MappedSuperclass.
    // Spring Data JPA validiert JPQL-Queries beim Start und
    // kann Felder aus @MappedSuperclass manchmal nicht
    // auflösen → Native Query umgeht dieses Problem sicher.

    @Query(value = """
            SELECT * FROM manufacturing_bom
             WHERE master_id = :masterId
                OR id        = :masterId
             ORDER BY revision ASC
            """,
           nativeQuery = true)
    List<ManufacturingBOM> findRevisionChain(
            @Param("masterId") Long masterId);
}