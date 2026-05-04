package com.peb.model.ebom;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "auxiliary_revisions")
public class AuxiliaryRevision extends EBOMRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auxiliary_id", nullable = false)
    private Auxiliary auxiliary;

    @Override
    public String getObjectId() {
        return auxiliary != null ? auxiliary.getId() : null;
    }

    @Override
    public EBOMObject getEbomObject() {
        return auxiliary;
    }
}