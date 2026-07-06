// src/main/java/com/peb/model/product/BaseRevision.java
package com.peb.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(insertable = false)   // ← Oracle GENERATED ALWAYS fix
    private Long revisionId;

    @Column(nullable = false, length = 10)
    private String revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ManufacturingStatus status = ManufacturingStatus.WORKING;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "last_modified_by", length = 100)
    private String lastModifiedBy;

    // ── Convenience ──────────────────────────────────────────
    public boolean isWorking() {
        return ManufacturingStatus.WORKING  == this.status;
    }
    public boolean isReleased() {
        return ManufacturingStatus.RELEASED == this.status;
    }
    public boolean isObsolete() {
        return ManufacturingStatus.OBSOLETE == this.status;
    }
}