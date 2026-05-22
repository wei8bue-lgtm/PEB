// src/main/java/com/peb/model/product/ProductBOM.java
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
 * Product BOM – fasst eine Baugruppenstruktur zusammen.
 * Wird genau einer ProductData zugewiesen (1:1).
 */
@Data
@NoArgsConstructor
@ToString(exclude = {"productData", "manufacturingBOMs"})
@Entity
@Table(name = "product_bom")
@EntityListeners(AuditingEntityListener.class)
public class ProductBOM {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "product_bom_seq")
    @SequenceGenerator(name       = "product_bom_seq",
                       sequenceName = "product_bom_seq",
                       allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Size(max = 20)
    @Column(name = "bom_version", length = 20)
    private String bomVersion = "01"; // Umbenennung → kein Konflikt
                                       // mit BaseObject.version (Long)

    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    // ── Beziehungen ──────────────────────────────────────────────

    /**
     * 1:1 zu ProductData.
     * FK liegt in dieser Tabelle (product_data_id).
     * UNIQUE-Constraint sichert die 1:1-Kardinalität auf DB-Ebene.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_data_id", unique = true)
    private ProductData productData;

    /**
     * n:m zu ManufacturingBOM.
     * Eine ProductBOM kann in mehreren ManufacturingBOMs verwendet
     * werden (Rückseite der Beziehung).
     */
    @ManyToMany(mappedBy = "productBOMs")
    private List<ManufacturingBOM> manufacturingBOMs =
            new ArrayList<>();

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