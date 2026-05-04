package com.peb.model.ebom;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "system_revisions")
public class SystemRevision extends EBOMRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", nullable = false)
    private System system;

    @Override
    public String getObjectId() {
        return system != null ? system.getId() : null;
    }

    @Override
    public EBOMObject getEbomObject() {
        return system;
    }
}