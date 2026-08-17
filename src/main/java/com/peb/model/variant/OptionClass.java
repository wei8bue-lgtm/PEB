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
 * A group of related, mutually-exclusive (or multi-select) variant options.
 *
 * Examples:
 *   id="ENGINE_TYPE"  name="Motortyp"   mandatory=true
 *   id="COLOR"        name="Farbe"      mandatory=false
 *
 * Stored in table OPTION_CLASSES.
 */
@Data
@Entity
@Table(name = "option_classes")
@EntityListeners(AuditingEntityListener.class)
public class OptionClass {

    /** Human-readable technical key, e.g. "ENGINE_TYPE". */
    @Id
    @Column(name = "id", length = 50)
    private String id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * If true, exactly one option from this class MUST be selected
     * in every valid configuration (single-select mandatory).
     * If false, the class is optional (0 or more options may be chosen).
     */
    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    /**
     * If true, only one option may be selected (radio-button semantics).
     * If false, multiple options of this class can be combined (checkbox).
     */
    @Column(name = "single_select", nullable = false)
    private boolean singleSelect = true;

    @OneToMany(mappedBy = "optionClass",
               cascade   = CascadeType.ALL,
               orphanRemoval = true,
               fetch     = FetchType.LAZY)
    @OrderBy("sortOrder ASC, code ASC")
    private List<VariantOption> options = new ArrayList<>();

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
