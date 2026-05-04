package com.peb.model.structure;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree node for structure visualization
 */
@Data
public class StructureNode {
    private String objectId;
    private Long revisionId;
    private String name;
    private String revision;
    private String releaseStatus;
    private String objectType;
    private Integer quantity;
    private String positionNumber;
    private List<StructureNode> children = new ArrayList<>();
    private boolean released;
}