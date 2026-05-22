// src/main/java/com/peb/model/product/PlantBOP.java
package com.peb.model.product;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plant BOP – oberste Ebene der Prozessplanung.
 * Enthält eine Baumstruktur (PlantBOPNode) mit Ordnern,
 * ProductBOPs, ManufacturingBOMs und BOEs.
 */
@Data
@NoArgsConstructor
@ToString(exclude = "nodes")
@Entity
@Table(name = "plant_bop")
@EntityListeners(AuditingEntityListener.class)
public class PlantBOP {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "plant_bop_seq")
    @SequenceGenerator(name         = "plant_bop_seq",
                       sequenceName = "plant_bop_seq",
                       allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Size(max = 20)
    @Column(name = "bop_version", length = 20)
    private String bopVersion = "01";

    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    @Size(max = 50)
    @Column(name = "plant_code", length = 50)
    private String plantCode;

    /** Alle Knoten (Root + Kinder); Root hat parent == null */
    @OneToMany(mappedBy     = "plantBOP",
               cascade      = CascadeType.ALL,
               orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PlantBOPNode> nodes = new ArrayList<>();

    // ── Auditing ─────────────────────────────────────────────────
    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    // ── Hilfsmethode ─────────────────────────────────────────────
    /** Nur Root-Knoten (parent == null), sortiert nach sortOrder */
    public List<PlantBOPNode> getRootNodes() {
        if (nodes == null) return List.of();
        return nodes.stream()
                .filter(n -> n.getParent() == null)
                .sorted(Comparator.comparingInt(
                        n -> n.getSortOrder() != null
                                ? n.getSortOrder() : 0))
                .toList();
    }
}