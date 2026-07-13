package com.peb.repository;

import com.peb.model.eclass.ObjectPropertyValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ObjectPropertyValueRepository extends JpaRepository<ObjectPropertyValue, Long> {

    List<ObjectPropertyValue> findByClassificationId(Long classificationId);

    void deleteByClassificationId(Long classificationId);
}
