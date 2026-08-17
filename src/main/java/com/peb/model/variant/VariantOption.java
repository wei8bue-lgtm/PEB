package com.peb.model.variant;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * A single selectable feature value within an {@link OptionClass}.
 *
 * Examples (OptionClass ENGINE_TYPE):
 *   code="V6"  name="V6 Motor 3.0L"
 *   code="V8"  name="V8 Motor 4.0L"
 *
 * The fully qualified option reference used in configRule expressions is:
 *   <optionClassId>=<code>    →   ENGINE_TYPE=V8
 *
 * Stored in table VARIANT_OPTIONS.
 */
@Data
@Entity
@Table(name = "variant_options",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_variant_option_class_code",
               columnNames = {"option_class_id", "code"}))
@EntityListeners(AuditingEntityListener.class)
public class VariantOption {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator  = "variant_option_seq")
    @SequenceGenerator(name           = "variant_option_seq",
                       sequenceName   = "VARIANT_OPTION_SEQ",
                       allocationSize = 1)
    @Column(name = "id")
    private Long id;

    /** Short technical code, e.g. "V8". Unique within the option class. */
    @NotBlank
    @Size(max = 50)
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    /** Display order within the option class. */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_class_id", nullable = false)
    private OptionClass optionClass;

    // ── Audit ──────────────────────────────────────────────────

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    // ── Convenience ────────────────────────────────────────────

    /**
     * Returns the expression token used in configRule strings:
     * "ENGINE_TYPE=V8"
     */
    public String toRuleToken() {
        return optionClass.getId() + "=" + code;
    }
}
