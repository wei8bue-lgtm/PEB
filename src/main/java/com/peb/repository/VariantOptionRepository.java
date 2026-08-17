package com.peb.repository;

import com.peb.model.variant.VariantOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VariantOptionRepository extends JpaRepository<VariantOption, Long> {

    List<VariantOption> findByOptionClassIdOrderBySortOrderAscCodeAsc(String optionClassId);

    boolean existsByOptionClassIdAndCode(String optionClassId, String code);
}
