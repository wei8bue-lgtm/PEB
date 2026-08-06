package com.peb.repository;

import com.peb.model.material.MaterialSupplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialSupplierRepository extends JpaRepository<MaterialSupplier, Long> {
    List<MaterialSupplier> findByMaterialIdOrderByPreferredDescApprovedDesc(String materialId);
}
