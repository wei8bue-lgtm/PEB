package com.peb.model.ebom;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "subsystem_revisions")
public class SubsystemRevision extends EBOMRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subsystem_id", nullable = false)
    private Subsystem subsystem;

    @Override
    public String getObjectId() {
        return subsystem != null ? subsystem.getId() : null;
    }

    @Override
    public EBOMObject getEbomObject() {
        return subsystem;
    }
}