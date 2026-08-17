package com.peb.repository.cim;

import com.peb.model.cim.CimAttributeSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CimAttributeSetRepository extends JpaRepository<CimAttributeSet, Long> {

    Optional<CimAttributeSet> findByCimRevisionRevisionId(Long cimRevisionId);
}
