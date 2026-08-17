package com.peb.repository;

import com.peb.model.variant.ProductConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductConfigurationRepository
        extends JpaRepository<ProductConfiguration, Long> {

    List<ProductConfiguration> findByRootObjectIdOrderByCreatedDateDesc(String rootObjectId);

    @Query("SELECT pc FROM ProductConfiguration pc " +
           "LEFT JOIN FETCH pc.selectedOptions " +
           "WHERE pc.id = :id")
    java.util.Optional<ProductConfiguration> findByIdWithOptions(@Param("id") Long id);
}
