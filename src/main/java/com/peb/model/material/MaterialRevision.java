package com.peb.model.material;

import com.peb.util.ReleaseStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Revision eines Materials (analog DesignRevision/PartRevision).
 * Bildet den Freigabeworkflow für Spezifikationsänderungen ab.
 */
@Data
@Entity
@Table(
    name = "material_revisions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_material_revision",
        columnNames = {"material_id", "revision"}
    ),
    indexes = @Index(name = "idx_material_rev_material", columnList = "material_id")
)
public class MaterialRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "material_rev_seq")
    @SequenceGenerator(name = "material_rev_seq", sequenceName = "MATERIAL_REV_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    /** Revisionsbezeichner, z.B. "A", "B", "C", ... */
    @Column(name = "revision", length = 10, nullable = false)
    private String revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_status", length = 20)
    private ReleaseStatus releaseStatus;

    @Lob
    @Column(name = "change_description")
    private String changeDescription;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @Column(name = "released_by", length = 100)
    private String releasedBy;

    @Column(name = "released_date")
    private LocalDateTime releasedDate;
}
