package com.peb.model.eclass;

import jakarta.persistence.*;
import lombok.Data;

/**
 * ECLASS Merkmal / Characteristic property definition.
 * Gehört zu genau einer {@link EclassClass}.
 */
@Data
@Entity
@Table(
    name = "eclass_properties",
    indexes = {
        @Index(name = "idx_eclass_prop_irdi",     columnList = "irdi"),
        @Index(name = "idx_eclass_prop_class_id",  columnList = "class_id")
    }
)
public class EclassProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eclass_prop_seq")
    @SequenceGenerator(name = "eclass_prop_seq", sequenceName = "ECLASS_PROP_SEQ", allocationSize = 50)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")   // nullable – wird immer via Cascade gesetzt
    private EclassClass eclassClass;

    /**
     * Merkmal-IRDI, z.B. "0173-1#02-AAB979#003"
     */
    @Column(name = "irdi", length = 60)
    private String irdi;

    /**
     * Merkmalname (deutsch oder englisch)
     */
    @Column(name = "name", length = 500)
    private String name;

    /**
     * Englischer Merkmalname
     */
    @Column(name = "name_en", length = 500)
    private String nameEn;

    /**
     * Einheit, z.B. "kW", "mm", "kg"
     */
    @Column(name = "unit", length = 50)
    private String unit;

    /**
     * Datentyp laut ECLASS, z.B. "REAL_MEASURE_TYPE", "STRING_TYPE", "BOOLEAN_TYPE"
     */
    @Column(name = "data_type", length = 50)
    private String dataType;

    /**
     * Reihenfolge der Merkmale innerhalb der Klasse
     */
    @Column(name = "sort_order")
    private Integer sortOrder;
}
