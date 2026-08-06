package com.peb.model.material;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Compliance-Daten eines Materials: REACH, RoHS, Konfliktmineralien.
 * 1:1-Beziehung zu {@link Material}.
 */
@Data
@Entity
@Table(name = "material_compliance")
public class MaterialCompliance {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "material_compl_seq")
    @SequenceGenerator(name = "material_compl_seq", sequenceName = "MATERIAL_COMPL_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false, unique = true)
    private Material material;

    // ── REACH ────────────────────────────────────────────────────
    /** Enthält das Material SVHC-Stoffe über dem 0,1%-Schwellenwert? */
    @Column(name = "reach_svhc")
    private Boolean reachSvhc;

    @Column(name = "reach_svhc_substances", length = 1000)
    private String reachSvhcSubstances;

    @Column(name = "reach_checked_date")
    private LocalDate reachCheckedDate;

    // ── RoHS ─────────────────────────────────────────────────────
    @Column(name = "rohs_compliant")
    private Boolean rohsCompliant;

    @Column(name = "rohs_checked_date")
    private LocalDate rohsCheckedDate;

    // ── Konfliktmineralien ───────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_minerals_status", length = 30)
    private ConflictMineralsStatus conflictMineralsStatus;

    // ── Dokumentation ────────────────────────────────────────────
    @Column(name = "compliance_document_path", length = 500)
    private String complianceDocumentPath;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Lob
    @Column(name = "notes")
    private String notes;

    @Column(name = "last_modified_by", length = 100)
    private String lastModifiedBy;

    @UpdateTimestamp
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    public enum ConflictMineralsStatus {
        DRC_CONFLICT_FREE, NOT_APPLICABLE, UNDER_REVIEW, NON_COMPLIANT, UNKNOWN
    }
}
