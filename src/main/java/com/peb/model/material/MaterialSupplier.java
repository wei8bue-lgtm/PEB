package com.peb.model.material;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lieferanten-/Beschaffungseintrag für ein Material (Approved Vendor List).
 * Ein Material kann mehrere Lieferanten haben (n:1 zu Material).
 */
@Data
@Entity
@Table(
    name = "material_suppliers",
    indexes = @Index(name = "idx_matsupplier_material", columnList = "material_id")
)
public class MaterialSupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "material_supplier_seq")
    @SequenceGenerator(name = "material_supplier_seq", sequenceName = "MATERIAL_SUPPLIER_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "supplier_name", length = 255, nullable = false)
    private String supplierName;

    @Column(name = "supplier_part_number", length = 100)
    private String supplierPartNumber;

    @Column(name = "price", precision = 12, scale = 4)
    private BigDecimal price;

    @Column(name = "currency", length = 3)
    private String currency = "EUR";

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "min_order_quantity")
    private Integer minOrderQuantity;

    /** Freigegebener Lieferant (AVL-Status) */
    @Column(name = "approved")
    private Boolean approved = false;

    /** Bevorzugter Lieferant */
    @Column(name = "preferred")
    private Boolean preferred = false;

    @Column(name = "notes", length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;
}
