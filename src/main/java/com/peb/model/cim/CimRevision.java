package com.peb.model.cim;

import com.peb.model.base.BaseRevision;
import com.peb.util.ReleaseStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "cim_revisions")
public class CimRevision extends BaseRevision {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cim_id", nullable = false)
    private CimObject cimObject;

    @Column(name = "source_mae_object_id", nullable = false, length = 12)
    private String sourceMaeObjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_mae_object_type", nullable = false, length = 30)
    private com.peb.util.ObjectType sourceMaeObjectType;

    @Column(name = "source_mae_revision_id")
    private Long sourceMaeRevisionId;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "release_comment", length = 1000)
    private String releaseComment;

    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    @Override
    public String getObjectId() {
        return cimObject != null ? cimObject.getId() : null;
    }

    public boolean isReleased() {
        return getReleaseStatus() == ReleaseStatus.RELEASED;
    }
}
