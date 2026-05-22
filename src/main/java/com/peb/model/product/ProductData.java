// src/main/java/com/peb/model/product/ProductData.java
package com.peb.model.product;

import com.peb.model.base.BaseObject;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ProductData – Baugruppenstruktur (z.B. Akku-Bohrmaschine)
 * ID-Format: PD00000001 ... PD99999999  (10 Zeichen = Prefix PD + 8 Ziffern)
 * Analog zu Design (DS + 8 Ziffern).
 *
 * Auditing (createdBy, createdDate, lastModifiedBy, lastModifiedDate)
 * erfolgt automatisch über AuditingEntityListener aus BaseObject.
 * version (optimistic locking) kommt ebenfalls aus BaseObject.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "product_data")
public class ProductData extends BaseObject {

    // description ist NICHT in BaseObject → hier definieren
    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    // Produkttyp (z.B. "Akku Bohrmaschine", "Elektromotor")
    @Size(max = 100)
    @Column(name = "product_type", length = 100)
    private String productType;

    // Status der ProductData
    @Enumerated(EnumType.STRING)
    @Column(name = "pd_status", length = 50)
    private ProductDataStatus status = ProductDataStatus.DRAFT;

    // 1:1 Beziehung zu ProductBOM (Kardinalität 1:1 gemäß Anforderung)
    // mappedBy = ProductBOM hält den FK
    @OneToOne(mappedBy = "productData",
              cascade = CascadeType.ALL,
              fetch = FetchType.LAZY,
              orphanRemoval = false)
    private ProductBOM productBOM;

    // ── Status Enum ──────────────────────────────────────────────
    public enum ProductDataStatus {
        DRAFT(   "Draft",    "🟡"),
        ACTIVE(  "Active",   "🟢"),
        RELEASED("Released", "✅"),
        OBSOLETE("Obsolete", "🔴");

        private final String displayName;
        private final String icon;

        ProductDataStatus(String displayName, String icon) {
            this.displayName = displayName;
            this.icon        = icon;
        }

        public String getDisplayName() { return displayName; }
        public String getIcon()        { return icon;        }
    }
}