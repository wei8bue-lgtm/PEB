package com.peb.model.cad;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a parent-child relationship between two Design objects.
 * Analogous to EbomStructure but purely within the Design domain.
 *
 * One Design revision can contain N child Design revisions.
 * The UNIQUE constraint on (PARENT_REVISION_ID, CHILD_REVISION_ID)
 * prevents duplicate links at DB level.
 */
@Entity
@Table(
    name = "DESIGN_STRUCTURE",
    uniqueConstraints = @UniqueConstraint(
        name       = "UQ_DS_STRUCT_PARENT_CHILD",
        columnNames = {"PARENT_REVISION_ID", "CHILD_REVISION_ID"}
    ),
    indexes = {
        @Index(name = "IDX_DS_STRUCT_PARENT_REV",
               columnList = "PARENT_REVISION_ID"),
        @Index(name = "IDX_DS_STRUCT_CHILD_REV",
               columnList = "CHILD_REVISION_ID"),
        @Index(name = "IDX_DS_STRUCT_PARENT_ID",
               columnList = "PARENT_ID"),
        @Index(name = "IDX_DS_STRUCT_CHILD_ID",
               columnList = "CHILD_ID")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator  = "ds_struct_seq")
    @SequenceGenerator(
        name           = "ds_struct_seq",
        sequenceName   = "DS_STRUCT_SEQ",
        allocationSize = 1
    )
    @Column(name = "STRUCTURE_ID")
    private Long structureId;

    // ── Parent side ───────────────────────────────────────────────────────
    /** Design.id of the parent (e.g. "DS00000001"). */
    @Column(name = "PARENT_ID", nullable = false, length = 20)
    private String parentId;

    /** DesignRevision.revisionId of the parent revision. */
    @Column(name = "PARENT_REVISION_ID", nullable = false)
    private Long parentRevisionId;

    // ── Child side ────────────────────────────────────────────────────────
    /** Design.id of the child (e.g. "DS00000002"). */
    @Column(name = "CHILD_ID", nullable = false, length = 20)
    private String childId;

    /** DesignRevision.revisionId of the child revision. */
    @Column(name = "CHILD_REVISION_ID", nullable = false)
    private Long childRevisionId;

    // ── BOM metadata ──────────────────────────────────────────────────────
    @Column(name = "QUANTITY", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    /** Optional position number within the parent (e.g. "10", "20"). */
    @Column(name = "POSITION_NUMBER", length = 50)
    private String positionNumber;

    // ── Audit ─────────────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "CREATED_DATE", updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;
}