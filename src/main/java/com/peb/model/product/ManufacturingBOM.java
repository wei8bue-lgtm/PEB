// src/main/java/com/peb/model/product/ManufacturingBOM.java
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

@Data
@NoArgsConstructor
@ToString(exclude = {"productBOMs", "operations", "productBOPs"})
@Entity
@Table(name = "manufacturing_bom")
@EntityListeners(AuditingEntityListener.class)
public class ManufacturingBOM {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "mfg_bom_seq")
    @SequenceGenerator(name         = "mfg_bom_seq",
                       sequenceName = "mfg_bom_seq",
                       allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Size(max = 20)
    @Column(name = "bom_version", length = 20)
    private String bomVersion = "01";

    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    // ── FIX: @OrderBy statt @OrderColumn ─────────────────────────
    // @OrderColumn erzeugt eine zusätzliche Spalte in der Join-Tabelle
    // und kann ebenfalls MultipleBagFetchException auslösen.
    // @OrderBy verwendet eine SQL ORDER BY Klausel – sicherer.

    @ManyToMany
    @JoinTable(
        name = "mfg_bom_product_bom",
        joinColumns        = @JoinColumn(name = "mfg_bom_id"),
        inverseJoinColumns = @JoinColumn(name = "product_bom_id")
    )
    // KEIN @OrderColumn hier – verhindert Bag-Probleme
    private List<ProductBOM> productBOMs = new ArrayList<>();
	
	public void setOperations(List<ManufacturingOperation> operations) {
        this.operations = operations != null 
                ? operations 
                : new ArrayList<>();
    }

    @OneToMany(mappedBy      = "manufacturingBOM",
               cascade       = CascadeType.ALL,
               orphanRemoval = true)
    @OrderBy("sequenceNumber ASC NULLS LAST, id ASC")
    private List<ManufacturingOperation> operations =
            new ArrayList<>();

    @ManyToMany(mappedBy = "manufacturingBOMs")
    private List<ProductBOP> productBOPs = new ArrayList<>();

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