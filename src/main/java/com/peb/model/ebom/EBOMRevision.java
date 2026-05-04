package com.peb.model.ebom;

import com.peb.model.base.BaseRevision;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Base class for all eBOM revisions
 */
@Data
@EqualsAndHashCode(callSuper = true)
@MappedSuperclass
public abstract class EBOMRevision extends BaseRevision {

    @Column(name = "mass", length = 8)
    @Size(max = 8)
    private String mass;

    @Column(name = "material", length = 256)
    @Size(max = 256)
    private String material;

    @Column(name = "supplier", length = 256)
    @Size(max = 256)
    private String supplier;

    public abstract EBOMObject getEbomObject();

    /**
     * Synchronize attributes from parent object
     */
    public void syncFromParent() {
        EBOMObject parent = getEbomObject();
        if (parent != null) {
            this.setName(parent.getName());
            this.setDepartment(parent.getDepartment());
            this.mass = parent.getMass();
            this.supplier = parent.getSupplier();
        }
    }

    /**
     * Synchronize attributes to parent object
     */
    public void syncToParent() {
        EBOMObject parent = getEbomObject();
        if (parent != null) {
            parent.setName(this.getName());
            parent.setDepartment(this.getDepartment());
            parent.setMass(this.mass);
            parent.setSupplier(this.supplier);
        }
    }
}