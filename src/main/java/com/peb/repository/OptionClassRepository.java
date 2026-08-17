package com.peb.repository;

import com.peb.model.variant.OptionClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionClassRepository extends JpaRepository<OptionClass, String> {

    List<OptionClass> findAllByOrderByIdAsc();
}
