package com.peb.model.cad;

import com.peb.model.base.BaseRevision;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Design revision entity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "design_revisions")
public class DesignRevision extends BaseRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "design_id", nullable = false)
    private Design design;

    @Column(name = "mass", length = 8)
    @Size(max = 8)
    private String mass;

    @Column(name = "material", length = 256)
    @Size(max = 256)
    private String material;

    @Column(name = "jt_file_path")
    private String jtFilePath; // Path to JT format 3D file

    @Override
    public String getObjectId() {
        return design != null ? design.getId() : null;
    }

    /**
     * Synchronize attributes from parent object
     */
    public void syncFromParent() {
        if (design != null) {
            this.setName(design.getName());
            this.setDepartment(design.getDepartment());
            this.mass = design.getMass();
        }
    }

    /**
     * Synchronize attributes to parent object
     */
    public void syncToParent() {
        if (design != null) {
            design.setName(this.getName());
            design.setDepartment(this.getDepartment());
            design.setMass(this.mass);
        }
    }
}