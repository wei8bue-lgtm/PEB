// src/main/java/com/peb/model/product/ManufacturingBaseEntity.java
package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Gemeinsame Basis für alle Manufacturing-Entitäten.
 *
 * Stellt folgende Felder bereit:
 *   status         – WORKING | RELEASED | OBSOLETE
 *   revision       – "01", "02", …
 *   predecessorId  – ID des direkten Vorgängers
 *   masterId       – ID des ersten Objekts der Kette
 *   Audit-Felder   – createdDate, createdBy, …
 *
 * ALLE fünf Manufacturing-Entitäten müssen diese
 * Klasse erweitern:
 *   ✓ ProductData
 *   ✓ ProductBOM
 *   ✓ ManufacturingBOM
 *   ✓ ProductBOP
 *   ✓ PlantBOP
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class ManufacturingBaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ManufacturingStatus status =
            ManufacturingStatus.WORKING;

    @Column(length = 10)
    private String revision = "01";

    /**
     * ID des direkten Vorgänger-Objekts.
     * null = dieses Objekt hat keinen Vorgänger
     *        (erstes Objekt der Kette).
     */
    @Column(name = "predecessor_id")
    private Long predecessorId;

    /**
     * ID des Master-Objekts (erstes Objekt der Kette).
     * null = dieses Objekt IST das Master.
     *
     * Alle Objekte derselben Revisionskette teilen
     * dieselbe masterId.
     */
    @Column(name = "master_id")
    private Long masterId;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by",
            updatable = false,
            length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "last_modified_by", length = 100)
    private String lastModifiedBy;

    // ── Convenience ───────────────────────────────────────────────

    public boolean isWorking() {
        return ManufacturingStatus.WORKING.equals(status);
    }

    public boolean isReleased() {
        return ManufacturingStatus.RELEASED.equals(status);
    }

    public boolean isObsolete() {
        return ManufacturingStatus.OBSOLETE.equals(status);
    }

    public boolean isEditable() {
        return isWorking();
    }
}