package com.peb.repository;

import com.peb.model.structure.StructureRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StructureRelationRepository extends JpaRepository<StructureRelation, Long> {

    List<StructureRelation> findByParentIdAndParentRevisionId(String parentId, Long parentRevisionId);

    List<StructureRelation> findByChildId(String childId);

    void deleteByParentIdAndParentRevisionId(String parentId, Long parentRevisionId);
}