package com.peb.model.ebom;

import com.peb.model.base.BaseObject;
import com.peb.util.ObjectType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Base class for all eBOM objects
 */
@Data
@EqualsAndHashCode(callSuper = true)
@MappedSuperclass
public abstract class EBOMObject extends BaseObject {

    @Column(name = "mass", length = 8)
    @Size(max = 8)
    private String mass; // Format: "kg"

    @Column(name = "material", length = 256)
    @Size(max = 256)
    private String material;

    @Column(name = "supplier", length = 256)
    @Size(max = 256)
    private String supplier;

    @Column(name = "linked_design_id", length = 10)
    private String linkedDesignId;

    public abstract ObjectType getObjectType();
    
    public abstract List<? extends EBOMRevision> getRevisions();

    public EBOMRevision getLatestRevision() {
        List<? extends EBOMRevision> revisionList = getRevisions();
        if (revisionList == null || revisionList.isEmpty()) {
            return null;
        }
        return revisionList.stream()
            .max((r1, r2) -> {
                String rev1 = ((EBOMRevision) r1).getRevision();
                String rev2 = ((EBOMRevision) r2).getRevision();
                return rev1.compareTo(rev2);
            })
            .orElse(null);
    }

    public EBOMRevision getReleasedRevision() {
        List<? extends EBOMRevision> revisionList = getRevisions();
        if (revisionList == null || revisionList.isEmpty()) {
            return null;
        }
        return revisionList.stream()
            .filter(r -> ((EBOMRevision) r).getReleaseStatus() == com.peb.util.ReleaseStatus.RELEASED)
            .findFirst()
            .orElse(null);
    }
}