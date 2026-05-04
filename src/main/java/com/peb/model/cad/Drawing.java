package com.peb.model.cad;

import com.peb.model.base.BaseObject;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing object (CAD Drawing)
 * ID format: DR00000001, DR00000002, ...
 * Created at revision level
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "drawings")
public class Drawing extends BaseObject {

    @OneToMany(mappedBy = "drawing", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DrawingRevision> revisions = new ArrayList<>();

    public DrawingRevision getLatestRevision() {
        return revisions.stream()
            .max((r1, r2) -> r1.getRevision().compareTo(r2.getRevision()))
            .orElse(null);
    }

    public DrawingRevision getReleasedRevision() {
        return revisions.stream()
            .filter(r -> r.getReleaseStatus() == com.peb.util.ReleaseStatus.RELEASED)
            .findFirst()
            .orElse(null);
    }
}