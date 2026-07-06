// src/main/java/com/peb/model/product/ProductData.java
package com.peb.model.product;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@Table(name = "product_data")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@ToString(exclude = {"productBOM", "revisions"})
public class ProductData {

    @Id
    @Column(name = "id", length = 20)
    private String id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String name;

    @Column(name = "product_type", length = 100)
    private String productType;

    @Column(length = 100)
    private String department;

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

    @OneToOne(mappedBy = "productData",
              fetch    = FetchType.LAZY)
    private ProductBOM productBOM;

    @OneToMany(
        mappedBy      = "productData",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    @OrderBy("revision ASC")
    private List<ProductDataRevision> revisions
            = new ArrayList<>();

    // ── Convenience-Methoden ──────────────────────────────────

    /**
     * Neueste Revision – letzte in der @OrderBy-sortierten Liste.
     * Null-sicher.
     */
    public ProductDataRevision getLatestRevision() {
        if (revisions == null || revisions.isEmpty()) {
            return null;
        }
        return revisions.get(revisions.size() - 1);
    }

    /**
     * Freigegebene Revision (Status RELEASED).
     * Lambdas statt Methoden-Referenz auf Basisklasse
     * → vermeidet Compiler-Fehler bei Generics.
     */
    public ProductDataRevision getReleasedRevision() {
        return revisions.stream()
                .filter(r -> r.isReleased())              // ← Lambda statt Methodenreferenz
                .max(Comparator.comparing(
                        r -> r.getRevision()))            // ← Lambda statt Methodenreferenz
                .orElse(null);
    }

    /**
     * Status der neuesten Revision.
     * Gibt null zurück wenn keine Revision vorhanden.
     */
    public ManufacturingStatus getLatestStatus() {
        ProductDataRevision latest = getLatestRevision();
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