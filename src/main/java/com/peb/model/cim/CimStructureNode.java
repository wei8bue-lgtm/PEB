package com.peb.model.cim;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CimStructureNode {

    private String cimId;
    private Long cimRevisionId;
    private String name;
    private String revision;
    private String releaseStatus;
    private String sourceMaeObjectId;
    private String sourceMaeObjectType;
    private Integer quantity;
    private String positionNumber;
    private String referenceDesignator;
    private String bmk;
    private String usageContext;
    private String relationType;
    private String configRule;
    private String alternativeGroup;
    private Integer alternativePriority;
    private List<CimStructureNode> children = new ArrayList<>();
}
