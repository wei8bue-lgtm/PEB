package com.peb.model.cim;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "cim_structure_relations")
public class CimStructureRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cim_structure_seq")
    @SequenceGenerator(name = "cim_structure_seq", sequenceName = "CIM_STRUCTURE_SEQ", allocationSize = 1)
    @Column(name = "relation_id")
    private Long relationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_cim_revision_id", nullable = false)
    private CimRevision parentCimRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_cim_revision_id", nullable = false)
    private CimRevision childCimRevision;

    @Column(name = "quantity")
    private Integer quantity = 1;

    @Column(name = "position_number", length = 20)
    private String positionNumber;

    @Column(name = "reference_designator", length = 50)
    private String referenceDesignator;

    @Column(name = "bmk", length = 100)
    private String bmk;

    @Column(name = "usage_context", length = 64)
    private String usageContext;

    @Column(name = "relation_type", length = 32)
    private String relationType;

    @Column(name = "config_rule", length = 500)
    private String configRule;

    @Column(name = "alternative_group", length = 50)
    private String alternativeGroup;

    @Column(name = "alternative_priority")
    private Integer alternativePriority;
}
