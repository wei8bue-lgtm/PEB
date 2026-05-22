// src/main/java/com/peb/model/product/ManufacturingOperation.java
package com.peb.model.product;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Operation oder Prozess innerhalb einer ManufacturingBOM.
 * Enthält Sollzeit, Istzeit, Linientaktzeit, Wartezeit.
 * Kann als PDF (Arbeitsanweisung) ausgegeben werden.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "mfg_operation")
public class ManufacturingOperation {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE,
					generator = "mfg_op_seq")
	@SequenceGenerator(name           = "mfg_op_seq",
					   sequenceName   = "mfg_op_seq",
					   allocationSize = 1,   // ← Muss mit DB übereinstimmen
					   initialValue   = 1)
	@Column(name = "id")
	private Long id;

    @Size(max = 20)
    @Column(name = "operation_number", length = 20)
    private String operationNumber; // z.B. "OP010"

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Size(max = 2000)
    @Column(name = "description", length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", length = 50)
    private OperationType operationType = OperationType.OPERATION;

    // ── Zeitfelder (Sekunden, 3 Dezimalstellen) ─────────────────
    @Column(name = "cycle_time_target", precision = 10, scale = 3)
    private BigDecimal cycleTimeTarget; // Sollzeit

    @Column(name = "cycle_time_actual", precision = 10, scale = 3)
    private BigDecimal cycleTimeActual; // Istzeit

    @Column(name = "line_takt_time", precision = 10, scale = 3)
    private BigDecimal lineTaktTime;   // Linientaktzeit

    @Column(name = "wait_time", precision = 10, scale = 3)
    private BigDecimal waitTime;       // Wartezeit

    // ── Sonstige Felder ─────────────────────────────────────────
    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    @Size(max = 100)
    @Column(name = "workstation", length = 100)
    private String workstation;

    @Column(name = "worker_count")
    private Integer workerCount = 1;

    @Size(max = 500)
    @Column(name = "tools_required", length = 500)
    private String toolsRequired;

    @Size(max = 1000)
    @Column(name = "safety_notes", length = 1000)
    private String safetyNotes;

    @Lob
    @Column(name = "work_instructions")
    private String workInstructions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mfg_bom_id", nullable = false)
    private ManufacturingBOM manufacturingBOM;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    // ── Berechnungen ─────────────────────────────────────────────

    /**
     * Effizienz = Sollzeit / Istzeit * 100
     * Null wenn keine Zeiten vorhanden.
     */
    public BigDecimal getEfficiency() {
        if (cycleTimeTarget == null || cycleTimeActual == null
                || cycleTimeActual.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return cycleTimeTarget
                .divide(cycleTimeActual, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    // ── Enums ────────────────────────────────────────────────────
    public enum OperationType {
        OPERATION(  "Operation",   "⚙️"),
        PROCESS(    "Process",     "🔄"),
        INSPECTION( "Inspection",  "🔍"),
        TRANSPORT(  "Transport",   "🚛"),
        REWORK(     "Rework",      "🔨");

        private final String displayName;
        private final String icon;

        OperationType(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
        public String getIcon()        { return icon; }
    }
}