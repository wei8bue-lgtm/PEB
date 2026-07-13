package com.peb.model.eclass;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Konkreter Merkmalswert eines PEB-Objekts für eine ECLASS-Klassifizierung.
 * Beispiel: Part "PT00000042" hat Merkmal "Nennleistung" = "5.5" (kW)
 */
@Data
@Entity
@Table(
    name = "object_property_values",
    indexes = {
        @Index(name = "idx_opv_classification", columnList = "classification_id"),
        @Index(name = "idx_opv_property",       columnList = "property_id")
    }
)
public class ObjectPropertyValue {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "opv_seq")
    @SequenceGenerator(name = "opv_seq", sequenceName = "OPV_SEQ", allocationSize = 10)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classification_id", nullable = false)
    private ObjectClassification classification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private EclassProperty property;

    /**
     * Wert als String (unabhängig vom Datentyp des Merkmals).
     * Numerische Werte, Booleans und Strings werden alle als String gespeichert.
     */
    @Column(name = "value", length = 500)
    private String value;
}
