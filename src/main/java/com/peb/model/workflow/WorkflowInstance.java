package com.peb.model.workflow;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Workflow instance for release and revise operations
 */
@Data
@Entity
@Table(name = "workflow_instances")
@EntityListeners(AuditingEntityListener.class)
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "workflow_seq")
    @SequenceGenerator(name = "workflow_seq", sequenceName = "WORKFLOW_SEQ", allocationSize = 1)
    @Column(name = "workflow_id")
    private Long workflowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", nullable = false)
    private WorkflowType workflowType;

    @Column(name = "object_id", nullable = false)
    private String objectId;

    @Column(name = "revision_id", nullable = false)
    private Long revisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkflowStatus status = WorkflowStatus.PENDING;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
}