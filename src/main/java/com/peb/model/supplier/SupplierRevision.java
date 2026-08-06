package com.peb.model.supplier;

import com.peb.util.ReleaseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Revision eines Lieferanten-Stammdatensatzes, analog zu
 * com.peb.model.material.MaterialRevision. Pro Lieferant darf zu jedem
 * Zeitpunkt höchstens eine Revision im Status RELEASED sein
 * (siehe SupplierService#releaseRevision).
 */
@Entity
@Table(name = "supplier_revisions")
@Getter
@Setter
public class SupplierRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(nullable = false, length = 10)
    private String revision;

    @Column(length = 1000)
    private String changeDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReleaseStatus releaseStatus;

    private String createdBy;

    @Column(updatable = false)
    private LocalDateTime createdDate;

    private String releasedBy;
    private LocalDateTime releasedDate;

    @PrePersist
    protected void onCreate() {
        this.createdDate = LocalDateTime.now();
    }
}
