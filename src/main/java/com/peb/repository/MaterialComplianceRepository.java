package com.peb.repository;

import com.peb.model.material.MaterialCompliance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialComplianceRepository extends JpaRepository<MaterialCompliance, Long> {

    Optional<MaterialCompliance> findByMaterialId(String materialId);

    /** Für Wiedervorlage: Materialien deren Compliance-Prüfung fällig ist */
    List<MaterialCompliance> findByNextReviewDateBefore(LocalDate date);
}
