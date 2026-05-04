package com.peb.model.base;

import com.peb.util.ReleaseStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base class for all revisions
 * Contains revision number and release status
 */
@Data
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revision_seq")
    @SequenceGenerator(name = "revision_seq", sequenceName = "REVISION_SEQ", allocationSize = 1)
    @Column(name = "revision_id")
    private Long revisionId;

    @Column(name = "name", length = 256, nullable = false)
    @NotBlank
    @Size(max = 256)
    private String name;

    @Column(name = "department", length = 128)
    @Size(max = 128)
    private String department;

    @Column(name = "revision", length = 2, nullable = false)
    @NotBlank
    @Size(max = 2)
    private String revision = "01";

    @Enumerated(EnumType.STRING)
    @Column(name = "release_status", length = 10, nullable = false)
    private ReleaseStatus releaseStatus = ReleaseStatus.WORKING;

    @Column(name = "checked_out")
    private boolean checkedOut = false;

    @Column(name = "checked_out_by")
    private String checkedOutBy;

    @Column(name = "checked_out_date")
    private LocalDateTime checkedOutDate;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "last_modified_by")
    private String lastModifiedBy;

    @Version
    @Column(name = "version")
    private Long version;

    public abstract String getObjectId();
}