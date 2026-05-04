package com.peb.model.ebom;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "part_revisions")
public class PartRevision extends EBOMRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Override
    public String getObjectId() {
        return part != null ? part.getId() : null;
    }

    @Override
    public EBOMObject getEbomObject() {
        return part;
    }
}