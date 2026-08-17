package com.peb.repository.cim;

import com.peb.model.cim.CimManufacturingLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CimManufacturingLinkRepository extends JpaRepository<CimManufacturingLink, Long> {

    List<CimManufacturingLink> findBySourceCimRevisionRevisionId(Long cimRevisionId);
}
