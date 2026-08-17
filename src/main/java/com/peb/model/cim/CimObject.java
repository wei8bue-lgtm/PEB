package com.peb.model.cim;

import com.peb.model.base.BaseObject;
import com.peb.util.ObjectType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "cim_objects")
public class CimObject extends BaseObject {

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 20)
    private CimLifecycleState lifecycleState = CimLifecycleState.DRAFT;

    @Column(name = "source_mae_object_id", nullable = false, length = 12)
    private String sourceMaeObjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_mae_object_type", nullable = false, length = 30)
    private ObjectType sourceMaeObjectType;

    @Column(name = "source_mae_revision_id")
    private Long sourceMaeRevisionId;

    @OneToMany(mappedBy = "cimObject", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CimRevision> revisions = new ArrayList<>();

    public CimRevision getLatestRevision() {
        if (revisions == null || revisions.isEmpty()) {
            return null;
        }
        return revisions.stream()
                .max(Comparator.comparing(CimRevision::getRevision))
                .orElse(null);
    }

    public CimRevision getReleasedRevision() {
        if (revisions == null || revisions.isEmpty()) {
            return null;
        }
        return revisions.stream()
                .filter(r -> r.isReleased())
                .max(Comparator.comparing(CimRevision::getRevision))
                .orElse(null);
    }
}
