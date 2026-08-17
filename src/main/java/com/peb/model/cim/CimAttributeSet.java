package com.peb.model.cim;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "cim_attribute_sets")
public class CimAttributeSet {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cim_attr_seq")
    @SequenceGenerator(name = "cim_attr_seq", sequenceName = "CIM_ATTR_SEQ", allocationSize = 1)
    @Column(name = "attribute_set_id")
    private Long attributeSetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cim_revision_id", nullable = false)
    private CimRevision cimRevision;

    @Column(name = "material", length = 256)
    private String material;

    @Column(name = "mass", length = 8)
    private String mass;

    @Column(name = "supplier", length = 256)
    private String supplier;

    @Column(name = "eclass_code", length = 32)
    private String eclassCode;

    @Column(name = "manufacturing_criticality", length = 50)
    private String manufacturingCriticality;

    @Column(name = "compliance_flags", length = 1000)
    private String complianceFlags;

    @Column(name = "attribute_source", length = 40)
    private String attributeSource;
}
