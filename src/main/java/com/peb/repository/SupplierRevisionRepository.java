package com.peb.repository;

import com.peb.model.supplier.SupplierRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierRevisionRepository extends JpaRepository<SupplierRevision, Long> {
    List<SupplierRevision> findBySupplierIdOrderByCreatedDateAsc(String supplierId);
}
