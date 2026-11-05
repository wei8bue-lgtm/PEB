package com.peb.model.planning;

import com.peb.model.product.ManufacturingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "planning_revision", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "revision_number"}))
@Getter
@Setter
public class PlanningRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private PlanningProject project;
    @Column(name = "revision_number", nullable = false)
    private int number;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ManufacturingStatus status = ManufacturingStatus.WORKING;
    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String snapshot;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false, length = 100)
    private String createdBy;
    private LocalDateTime releasedAt;
    @Column(length = 100)
    private String releasedBy;
}