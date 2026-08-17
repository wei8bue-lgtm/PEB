package com.peb.model.cim;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cim_validation_results")
public class CimValidationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cim_val_seq")
    @SequenceGenerator(name = "cim_val_seq", sequenceName = "CIM_VAL_SEQ", allocationSize = 1)
    @Column(name = "validation_result_id")
    private Long validationResultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cim_revision_id", nullable = false)
    private CimRevision cimRevision;

    @Column(name = "rule_id", nullable = false, length = 80)
    private String ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private CimValidationSeverity severity;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "blocking", nullable = false)
    private boolean blocking;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;
}
