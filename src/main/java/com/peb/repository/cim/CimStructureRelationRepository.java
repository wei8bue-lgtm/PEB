package com.peb.repository.cim;

import com.peb.model.cim.CimStructureRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CimStructureRelationRepository extends JpaRepository<CimStructureRelation, Long> {

    List<CimStructureRelation> findByParentCimRevisionRevisionId(Long parentRevisionId);

    List<CimStructureRelation> findByChildCimRevisionRevisionId(Long childRevisionId);
}
