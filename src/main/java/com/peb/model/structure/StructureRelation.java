package com.peb.model.structure;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Structure relationship between objects
 * Supports both CAD Design objects and eBOM objects in structures
 */
@Data
@Entity
@Table(name = "structure_relations")
@EntityListeners(AuditingEntityListener.class)
public class StructureRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "structure_seq")
    @SequenceGenerator(name = "structure_seq", sequenceName = "STRUCTURE_SEQ", allocationSize = 1)
    @Column(name = "relation_id")
    private Long relationId;

    @Column(name = "parent_id", nullable = false)
    private String parentId;

    @Column(name = "parent_revision_id", nullable = false)
    private Long parentRevisionId;

    @Column(name = "child_id", nullable = false)
    private String childId;

    @Column(name = "child_revision_id", nullable = false)
    private Long childRevisionId;

    @Column(name = "quantity")
    private Integer quantity = 1;

    @Column(name = "position_number")
    private String positionNumber;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;
}