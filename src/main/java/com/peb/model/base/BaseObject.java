package com.peb.model.base;

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
 * Base class for all CAD and eBOM objects
 * Contains common attributes: ID, Name, Department
 */
@Data
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseObject {

    @Id
    @Column(name = "id", length = 10, nullable = false, unique = true)
    @NotBlank
    @Size(max = 10)
    private String id;

    @Column(name = "name", length = 256, nullable = false)
    @NotBlank
    @Size(max = 256)
    private String name;

    @Column(name = "department", length = 128)
    @Size(max = 128)
    private String department;

    @Column(name = "thumbnail_path")
    private String thumbnailPath;

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
}