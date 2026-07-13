package com.peb.repository;

import com.peb.model.eclass.EclassProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EclassPropertyRepository extends JpaRepository<EclassProperty, Long> {

    Optional<EclassProperty> findByIrdi(String irdi);

    List<EclassProperty> findByEclassClassIdOrderBySortOrder(Long classId);
}
