package com.peb.repository.cim;

import com.peb.model.cim.CimValidationResult;
import com.peb.model.cim.CimValidationSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CimValidationResultRepository extends JpaRepository<CimValidationResult, Long> {

    List<CimValidationResult> findByCimRevisionRevisionId(Long cimRevisionId);

    List<CimValidationResult> findByCimRevisionRevisionIdAndBlockingTrue(Long cimRevisionId);

    List<CimValidationResult> findByCimRevisionRevisionIdAndSeverity(Long cimRevisionId, CimValidationSeverity severity);

    void deleteByCimRevisionRevisionId(Long cimRevisionId);
}
