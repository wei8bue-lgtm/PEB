// src/main/java/com/peb/model/product/ProductBOM.java
package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "product_bom")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@ToString(exclude = {"productData", "manufacturingBOMs",
                     "revisions"})
public class ProductBOM {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "product_bom_seq")
    @SequenceGenerator(name           = "product_bom_seq",
                       sequenceName   = "PRODUCT_BOM_SEQ",
                       allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "bom_version", length = 10)
    private String bomVersion = "01";

    @Column(columnDefinition = "CLOB")
    private String description;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by",
            updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "last_modified_by", length = 100)
    private String lastModifiedBy;

    // ── Beziehungen ───────────────────────────────────────────

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_data_id")
    private ProductData productData;

    @ManyToMany(mappedBy = "productBOMs",
                fetch    = FetchType.LAZY)
    private List<ManufacturingBOM> manufacturingBOMs
            = new ArrayList<>();

    @OneToMany(
        mappedBy      = "productBOM",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    @OrderBy("revision ASC")
    private List<ProductBOMRevision> revisions
            = new ArrayList<>();

    // ── Convenience-Methoden ──────────────────────────────────

    /**
     * Neueste Revision – letzte in der sortierten Liste.
     */
    public ProductBOMRevision getLatestRevision() {
        if (revisions == null || revisions.isEmpty()) {
            return null;
        }
        return revisions.get(revisions.size() - 1);
    }

    /**
     * Freigegebene Revision (Status RELEASED).
     */
    public ProductBOMRevision getReleasedRevision() {
        return revisions.stream()
                .filter(r -> r.isReleased())              // ← Lambda
                .max(Comparator.comparing(
                        r -> r.getRevision()))            // ← Lambda
                .orElse(null);
    }

    /**
     * Status der neuesten Revision.
     */
    public ManufacturingStatus getLatestStatus() {
        ProductBOMRevision latest = getLatestRevision();
        return latest != null
               ? latest.getStatus()
               : null;
    }

    /**
     * Status-Name für Thymeleaf – niemals null.
     */
    public String getLatestStatusName() {
        ManufacturingStatus status = getLatestStatus();
        return status != null
               ? status.name()
               : "KEINE_REVISION";
    }
}