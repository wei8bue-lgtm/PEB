package com.peb.repository;

import com.peb.model.base.Dataset;
import com.peb.model.ebom.DatasetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    // ── Basis-Methoden ────────────────────────────────────────────
    List<Dataset> findByRevisionIdAndRevisionType(
            Long revisionId, String revisionType);

    void deleteByRevisionIdAndRevisionType(
            Long revisionId, String revisionType);

    // ── Kategorie-Filter ──────────────────────────────────────────
    List<Dataset> findByRevisionIdAndRevisionTypeAndCategory(
            Long revisionId, String revisionType, DatasetCategory category);

    // ── Alle einer Revision (ohne Type-Filter) ────────────────────
    List<Dataset> findByRevisionId(Long revisionId);

    // ── Alle eines RevisionTypes ──────────────────────────────────
    List<Dataset> findByRevisionType(String revisionType);

    // ── Alle Datasets eines Objekts über alle Revisionen ─────────

    /**
     * Alle Datasets für ein Design-Objekt (alle Revisionen)
     */
    @Query("""
            SELECT d FROM Dataset d
            WHERE d.revisionType = 'DESIGN'
            AND d.revisionId IN (
                SELECT dr.revisionId FROM DesignRevision dr
                WHERE dr.design.id = :objectId
            )
            ORDER BY d.createdDate DESC
            """)
    List<Dataset> findAllByDesignId(@Param("objectId") String objectId);

    /**
     * Alle Datasets für ein Drawing-Objekt (alle Revisionen)
     */
    @Query("""
            SELECT d FROM Dataset d
            WHERE d.revisionType = 'DRAWING'
            AND d.revisionId IN (
                SELECT dr.revisionId FROM DrawingRevision dr
                WHERE dr.drawing.id = :objectId
            )
            ORDER BY d.createdDate DESC
            """)
    List<Dataset> findAllByDrawingId(@Param("objectId") String objectId);

    /**
     * Alle Datasets für Machine (alle Revisionen)
     */
    @Query("""
            SELECT d FROM Dataset d
            WHERE d.revisionType = 'EBOM'
            AND d.revisionId IN (
                SELECT mr.revisionId FROM MachineRevision mr
                WHERE mr.machine.id = :objectId
            )
            ORDER BY d.createdDate DESC
            """)
    List<Dataset> findAllByMachineId(@Param("objectId") String objectId);

    /**
     * Alle Datasets für System (alle Revisionen)
     */
    @Query("""
            SELECT d FROM Dataset d
            WHERE d.revisionType = 'EBOM'
            AND d.revisionId IN (
                SELECT sr.revisionId FROM SystemRevision sr
                WHERE sr.system.id = :objectId
            )
            ORDER BY d.createdDate DESC
            """)
    List<Dataset> findAllBySystemId(@Param("objectId") String objectId);

    /**
     * Alle Datasets für Subsystem (alle Revisionen)
     */
    @Query("""
            SELECT d FROM Dataset d
            WHERE d.revisionType = 'EBOM'
            AND d.revisionId IN (
                SELECT sr.revisionId FROM SubsystemRevision sr
                WHERE sr.subsystem.id = :objectId
            )
            ORDER BY d.createdDate DESC
            """)
    List<Dataset> findAllBySubsystemId(@Param("objectId") String objectId);

    /**
     * Alle Datasets für Module (alle Revisionen)
     */
    @Query("""
            SELECT d FROM Dataset d
            WHERE d.revisionType = 'EBOM'
            AND d.revisionId IN (
                SELECT mr.revisionId FROM EBOMModuleRevision mr
                WHERE mr.module.id = :objectId
            )
            ORDER BY d.createdDate DESC
            """)
    List<Dataset> findAllByModuleId(@Param("objectId") String objectId);

    /**
     * Alle Datasets für Submodule (alle Revisionen)
     */
    @Query("""
            SELECT d FROM Dataset d
            WHERE d.revisionType = 'EBOM'
            AND d.revisionId IN (
                SELECT sr.revisionId FROM SubmoduleRevision sr
                WHERE sr.submodule.id = :objectId
            )
            ORDER BY d.createdDate DESC
            """)
    List<Dataset> findAllBySubmoduleId(@Param("objectId") String objectId);

    /**
     * Alle Datasets für Part (alle Revisionen)
     */
    @Query("""
            SELECT d FROM Dataset d
            WHERE d.revisionType = 'EBOM'
            AND d.revisionId IN (
                SELECT pr.revisionId FROM PartRevision pr
                WHERE pr.part.id = :objectId
            )
            ORDER BY d.createdDate DESC
            """)
    List<Dataset> findAllByPartId(@Param("objectId") String objectId);

    /**
     * Alle Datasets für Auxiliary (alle Revisionen)
     */
    @Query("""
            SELECT d FROM Dataset d
            WHERE d.revisionType = 'EBOM'
            AND d.revisionId IN (
                SELECT ar.revisionId FROM AuxiliaryRevision ar
                WHERE ar.auxiliary.id = :objectId
            )
            ORDER BY d.createdDate DESC
            """)
    List<Dataset> findAllByAuxiliaryId(@Param("objectId") String objectId);

}