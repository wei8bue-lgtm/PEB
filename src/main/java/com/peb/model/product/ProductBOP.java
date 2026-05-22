// src/main/java/com/peb/model/product/ProductBOP.java
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
import java.util.List;

/**
 * Product BOP (Bill of Process).
 * Enthält eine oder mehrere ManufacturingBOMs.
 */
@Data
@NoArgsConstructor
@ToString(exclude = {"manufacturingBOMs"})
@Entity
@Table(name = "product_bop")
@EntityListeners(AuditingEntityListener.class)
public class ProductBOP {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "product_bop_seq")
    @SequenceGenerator(name         = "product_bop_seq",
                       sequenceName = "product_bop_seq",
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

    /** 1..n ManufacturingBOMs in einer ProductBOP */
    @ManyToMany
    @JoinTable(
        name = "product_bop_mfg_bom",
        joinColumns        = @JoinColumn(name = "product_bop_id"),
        inverseJoinColumns = @JoinColumn(name = "mfg_bom_id")
    )
    private List<ManufacturingBOM> manufacturingBOMs = new ArrayList<>();

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
}