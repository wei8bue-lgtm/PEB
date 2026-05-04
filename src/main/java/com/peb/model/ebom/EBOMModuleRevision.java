package com.peb.model.ebom;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "module_revisions")
public class EBOMModuleRevision extends EBOMRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private EBOMModule module;

    @Override
    public String getObjectId() {
        return module != null ? module.getId() : null;
    }

    @Override
    public EBOMObject getEbomObject() {
        return module;
    }
}