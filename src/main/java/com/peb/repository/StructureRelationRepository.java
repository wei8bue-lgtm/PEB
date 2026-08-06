// src/main/java/com/peb/repository/StructureRelationRepository.java
package com.peb.repository;

import com.peb.model.structure.StructureRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StructureRelationRepository
        extends JpaRepository<StructureRelation, Long> {

    List<StructureRelation> findByParentIdAndParentRevisionId(
            String parentId, Long parentRevisionId);

    /** All direct children of a given child object
     *  (used to find where-used / parent lookup). */
	List<StructureRelation> findByChildId(String childId);

	/** Remove all structure links where the given object appears as child.
	 *  Used when a leaf object (e.g. a Document) is deleted, to avoid
	 *  orphaned structure links pointing at a non-existent object. */
	void deleteByChildId(String childId);   // ← NEU

    /** Bulk-reset BMK for an entire top-level structure tree
     *  before a re-generation run. */
    @Modifying
    @Query("""
           UPDATE StructureRelation sr
           SET sr.bmk = NULL,
               sr.bmkGeneratedAt = NULL,
               sr.bmkGeneratedBy = NULL
           WHERE sr.parentId = :rootId
              OR sr.parentId IN (
                   SELECT sr2.childId
                   FROM StructureRelation sr2
                   WHERE sr2.parentId = :rootId
              )
           """)
    int clearBmkForTree(@Param("rootId") String rootId);

    /** Update BMK fields for a single relation. */
    @Modifying
    @Query("""
           UPDATE StructureRelation sr
           SET sr.bmk             = :bmk,
               sr.bmkGeneratedAt  = :generatedAt,
               sr.bmkGeneratedBy  = :generatedBy
           WHERE sr.relationId    = :relationId
           """)
    int updateBmk(@Param("relationId")  Long          relationId,
                  @Param("bmk")         String        bmk,
                  @Param("generatedAt") LocalDateTime generatedAt,
                  @Param("generatedBy") String        generatedBy);
}