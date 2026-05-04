package com.peb.model.base;

import com.peb.model.ebom.DatasetCategory;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Dataset entity for file attachments to revisions
 */
@Data
@Entity
@Table(name = "datasets")
@EntityListeners(AuditingEntityListener.class)
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dataset_seq")
    @SequenceGenerator(name = "dataset_seq", sequenceName = "DATASET_SEQ", allocationSize = 1)
    @Column(name = "dataset_id")
    private Long datasetId;

    @Column(name = "revision_id", nullable = false)
    private Long revisionId;

    @Column(name = "revision_type", nullable = false)
    private String revisionType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private DatasetCategory category;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
}