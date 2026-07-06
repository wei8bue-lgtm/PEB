// src/main/java/com/peb/model/product/ManufacturingRevision.java
package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Repräsentiert eine einzelne Revision eines
 * Manufacturing-Objekts.
 *
 * ══════════════════════════════════════════════════════
 * ARCHITEKTUR-ENTSCHEIDUNG:
 * ══════════════════════════════════════════════════════
 *
 * Analog zu DesignRevision:
 *   Design (Master) ──1:N──► DesignRevision
 *
 * Für Manufacturing-Objekte gibt es FÜNF Master-Typen:
 *   ProductData      ──1:N──► ManufacturingRevision
 *   ProductBOM       ──1:N──► ManufacturingRevision
 *   ManufacturingBOM ──1:N──► ManufacturingRevision
 *   ProductBOP       ──1:N──► ManufacturingRevision
 *   PlantBOP         ──1:N──► ManufacturingRevision
 *
 * Da alle fünf Typen dieselben Revisionsfelder teilen,
 * wird eine gemeinsame Revisions-Tabelle mit
 * Diskriminator-Spalten verwendet.
 *
 * ══════════════════════════════════════════════════════
 * DATENBANK-TABELLE: manufacturing_revision
 * ══════════════════════════════════════════════════════
 *
 * Spalten:
 *   revision_id      – PK (Sequence)
 *   object_type      – Diskriminator: PRODUCT_DATA,
 *                      PRODUCT_BOM, MANUFACTURING_BOM,
 *                      PRODUCT_BOP, PLANT_BOP
 *   object_id        – FK auf das Master-Objekt (String
 *                      für ProductData, Long für andere)
 *   object_id_long   – FK für Long-basierte Master
 *   revision         – Revisionsnummer: "01", "02", …
 *   status           – WORKING | RELEASED | OBSOLETE
 *   created_date     – Erstellungszeitpunkt (automatisch)
 *   created_by       – Erstellt von (automatisch via
 *                      Spring Security Auditing)
 *   last_modified_date
 *   last_modified_by
 */
@Entity
@Table(
    name = "manufacturing_revision",
    indexes = {
        @Index(name = "idx_mfg_rev_object",
               columnList = "object_type, object_id_long"),
        @Index(name = "idx_mfg_rev_object_str",
               columnList = "object_type, object_id_str"),
        @Index(name = "idx_mfg_rev_status",
               columnList = "status")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@ToString
public class ManufacturingRevision {

    // ── Primary Key ───────────────────────────────────────────────

    @Id
    @GeneratedValue(
        strategy  = GenerationType.SEQUENCE,
        generator = "mfg_rev_seq"
    )
    @SequenceGenerator(
        name           = "mfg_rev_seq",
        sequenceName   = "MFG_REVISION_SEQ",
        allocationSize = 1
    )
    @Column(name = "revision_id")
    private Long revisionId;

    // ── Diskriminator: welcher Master-Typ ─────────────────────────

    /**
     * Typ des zugehörigen Master-Objekts.
     * Bestimmt welche FK-Spalte befüllt ist.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "object_type",
            nullable = false,
            length = 30)
    private ManufacturingObjectType objectType;

    // ── FK: Master-Objekt (zwei Varianten) ───────────────────────

    /**
     * FK für Long-basierte Master-Objekte:
     *   ProductBOM, ManufacturingBOM, ProductBOP, PlantBOP
     *
     * NULL wenn objectType == PRODUCT_DATA
     */
    @Column(name = "object_id_long")
    private Long objectIdLong;

    /**
     * FK für String-basierte Master-Objekte:
     *   ProductData (ID-Format: "PD00000001")
     *
     * NULL wenn objectType != PRODUCT_DATA
     */
    @Column(name = "object_id_str", length = 20)
    private String objectIdStr;

    // ── Revisionsdaten ────────────────────────────────────────────

    /**
     * Revisionsnummer: "01", "02", "03", …
     * Wird bei Revise inkrementiert.
     */
    @Column(name = "revision",
            nullable = false,
            length = 10)
    private String revision = "01";

    /**
     * Lifecycle-Status dieser Revision.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status",
            nullable = false,
            length = 20)
    private ManufacturingStatus status =
            ManufacturingStatus.WORKING;

    // ── Audit-Felder ──────────────────────────────────────────────

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by",
            updatable = false,
            length = 100)
    private String createdBy;

    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @Column(name = "last_modified_by", length = 100)
    private String lastModifiedBy;

    // ── Snapshot-Felder ───────────────────────────────────────────
    // Speichert den Namen des Master-Objekts zum Zeitpunkt
    // der Revision (analog zu DesignRevision.syncFromParent()).
    // Nützlich für historische Auswertungen wenn der Name
    // des Master-Objekts später geändert wird.

    /**
     * Name des Master-Objekts zum Zeitpunkt dieser Revision.
     */
    @Column(name = "object_name_snapshot", length = 255)
    private String objectNameSnapshot;

    /**
     * Version/BOM-Version des Master-Objekts
     * zum Zeitpunkt dieser Revision.
     */
    @Column(name = "version_snapshot", length = 10)
    private String versionSnapshot;

    // ── Enum: Objekt-Typen ────────────────────────────────────────

    /**
     * Diskriminator-Enum für den Master-Typ.
     */
    public enum ManufacturingObjectType {
        PRODUCT_DATA,
        PRODUCT_BOM,
        MANUFACTURING_BOM,
        PRODUCT_BOP,
        PLANT_BOP
    }

    // ── Convenience-Methoden ──────────────────────────────────────

    public boolean isWorking() {
        return ManufacturingStatus.WORKING.equals(status);
    }

    public boolean isReleased() {
        return ManufacturingStatus.RELEASED.equals(status);
    }

    public boolean isObsolete() {
        return ManufacturingStatus.OBSOLETE.equals(status);
    }

    // ── Statische Factory-Methoden ────────────────────────────────

    /**
     * Erstellt eine initiale Revision (Rev "01", WORKING)
     * für ein ProductData-Objekt.
     */
    public static ManufacturingRevision initialForProductData(
            String productDataId,
            String name) {

        ManufacturingRevision rev = new ManufacturingRevision();
        rev.setObjectType(
                ManufacturingObjectType.PRODUCT_DATA);
        rev.setObjectIdStr(productDataId);
        rev.setRevision("01");
        rev.setStatus(ManufacturingStatus.WORKING);
        rev.setObjectNameSnapshot(name);
        return rev;
    }

    /**
     * Erstellt eine initiale Revision (Rev "01", WORKING)
     * für Long-basierte Master-Objekte.
     */
    public static ManufacturingRevision initialFor(
            Long objectId,
            ManufacturingObjectType type,
            String name,
            String version) {

        ManufacturingRevision rev = new ManufacturingRevision();
        rev.setObjectType(type);
        rev.setObjectIdLong(objectId);
        rev.setRevision("01");
        rev.setStatus(ManufacturingStatus.WORKING);
        rev.setObjectNameSnapshot(name);
        rev.setVersionSnapshot(version);
        return rev;
    }

    /**
     * Erstellt die nächste Revision auf Basis
     * einer bestehenden Revision.
     * Status: WORKING, Revisionsnummer inkrementiert.
     */
    public ManufacturingRevision nextRevision() {
        ManufacturingRevision next = new ManufacturingRevision();
        next.setObjectType(this.objectType);
        next.setObjectIdLong(this.objectIdLong);
        next.setObjectIdStr(this.objectIdStr);
        next.setRevision(incrementRevision(this.revision));
        next.setStatus(ManufacturingStatus.WORKING);
        next.setObjectNameSnapshot(this.objectNameSnapshot);
        next.setVersionSnapshot(this.versionSnapshot);
        return next;
    }

    /**
     * Synchronisiert den Name-Snapshot vom Master-Objekt.
     * Analog zu DesignRevision.syncFromParent().
     */
    public void syncSnapshot(String name, String version) {
        this.objectNameSnapshot = name;
        this.versionSnapshot    = version;
    }

    // ── Private Hilfsmethoden ─────────────────────────────────────

    /**
     * "01" → "02", "09" → "10", "99" → "100"
     * Analog zu DrawingRevisionService.nextRevisionLabel()
     */
    private String incrementRevision(String current) {
        if (current == null || current.isBlank()) {
            return "02";
        }
        try {
            int num      = Integer.parseInt(current.trim());
            int padWidth = Math.max(current.trim().length(), 2);
            return String.format("%0" + padWidth + "d", num + 1);
        } catch (NumberFormatException e) {
            return current + "_1";
        }
    }
}