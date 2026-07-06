package com.peb.repository;

import com.peb.model.cad.DesignStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DesignStructureRepository
        extends JpaRepository<DesignStructure, Long> {

    // ── Child-side queries ────────────────────────────────────────────────

    /** All direct children of a specific parent revision. */
    List<DesignStructure> findByParentRevisionId(Long parentRevisionId);

    /** All parent-links where a design revision appears as child. */
    List<DesignStructure> findByChildRevisionId(Long childRevisionId);

    // ── Combined lookup ───────────────────────────────────────────────────

    Optional<DesignStructure> findByParentRevisionIdAndChildRevisionId(
            Long parentRevisionId, Long childRevisionId);

    boolean existsByParentRevisionIdAndChildRevisionId(
            Long parentRevisionId, Long childRevisionId);

    // ── Design-level queries (across all revisions) ───────────────────────

    /** All structure rows where Design.id appears as parent. */
    @Query("""
           SELECT ds FROM DesignStructure ds
           WHERE ds.parentId = :parentId
           """)
    List<DesignStructure> findAllByParentDesignId(
            @Param("parentId") String parentId);

    /** All structure rows where Design.id appears as child.
     *  Used by the cycle-detection and "where-used" logic. */
    @Query("""
           SELECT ds FROM DesignStructure ds
           WHERE ds.childId = :childId
           """)
    List<DesignStructure> findAllByChildDesignId(
            @Param("childId") String childId);

    // ── Cascade-delete helpers ────────────────────────────────────────────

    /** Remove all child-links when a parent revision is deleted. */
    void deleteByParentRevisionId(Long parentRevisionId);

    /** Remove all parent-links when a child revision is deleted. */
    void deleteByChildRevisionId(Long childRevisionId);
}