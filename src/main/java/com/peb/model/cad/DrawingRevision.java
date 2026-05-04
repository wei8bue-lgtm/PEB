package com.peb.model.cad;

import com.peb.model.base.BaseRevision;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Drawing revision entity
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "drawing_revisions")
public class DrawingRevision extends BaseRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drawing_id", nullable = false)
    private Drawing drawing;

    @Override
    public String getObjectId() {
        return drawing != null ? drawing.getId() : null;
    }

    /**
     * Synchronize attributes from parent object
     */
    public void syncFromParent() {
        if (drawing != null) {
            this.setName(drawing.getName());
            this.setDepartment(drawing.getDepartment());
        }
    }

    /**
     * Synchronize attributes to parent object
     */
    public void syncToParent() {
        if (drawing != null) {
            drawing.setName(this.getName());
            drawing.setDepartment(this.getDepartment());
        }
    }
}