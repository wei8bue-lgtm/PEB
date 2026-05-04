package com.peb.model.ebom;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Revision entity for Machine
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "machine_revisions")
public class MachineRevision extends EBOMRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    @Override
    public String getObjectId() {
        return machine != null ? machine.getId() : null;
    }

    @Override
    public EBOMObject getEbomObject() {
        return machine;
    }
}