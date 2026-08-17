package com.peb.model.variant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a specific product configuration (a "100% BOM snapshot" key).
 *
 * By selecting a set of {@link VariantOption}s the system can resolve
 * the 150% BOM into the exact set of parts required for this configuration.
 *
 * Typical use: one entry per customer order or product variant.
 *
 * Stored in table PRODUCT_CONFIGURATIONS.
 */
@Data
@Entity
@Table(name = "product_configurations")
@EntityListeners(AuditingEntityListener.class)
public class ProductConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator  = "product_config_seq")
    @SequenceGenerator(name           = "product_config_seq",
                       sequenceName   = "PRODUCT_CONFIG_SEQ",
                       allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Optional order / project reference number. */
    @Size(max = 100)
    @Column(name = "order_number", length = 100)
    private String orderNumber;

    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    /**
     * The root eBOM object (typically a Machine) whose 150% BOM
     * this configuration is applied against.
     * Stored as a plain String ID to avoid a hard cross-module FK.
     */
    @Size(max = 12)
    @Column(name = "root_object_id", length = 12)
    private String rootObjectId;

    /**
     * The revision of the root object to resolve against.
     * Null means "use latest revision".
     */
    @Column(name = "root_revision_id")
    private Long rootRevisionId;

    /**
     * The concrete set of options chosen for this configuration.
     * Example: {ENGINE_TYPE=V8, COLOR=RED}
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name               = "config_selected_options",
        joinColumns        = @JoinColumn(name = "config_id"),
        inverseJoinColumns = @JoinColumn(name = "option_id")
    )
    private List<VariantOption> selectedOptions = new ArrayList<>();

    // ── Audit ──────────────────────────────────────────────────

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "last_modified_by", length = 100)
    private String lastModifiedBy;
}
