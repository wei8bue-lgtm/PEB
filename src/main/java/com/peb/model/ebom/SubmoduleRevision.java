package com.peb.model.ebom;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "submodule_revisions")
public class SubmoduleRevision extends EBOMRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submodule_id", nullable = false)
    private Submodule submodule;

    @Override
    public String getObjectId() {
        return submodule != null ? submodule.getId() : null;
    }

    @Override
    public EBOMObject getEbomObject() {
        return submodule;
    }
}