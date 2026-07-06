// src/main/java/com/peb/dto/StructureRelationDTO.java
package com.peb.dto;

import lombok.Data;

@Data
public class StructureRelationDTO {
    private String  parentId;
    private Long    parentRevisionId;
    private String  childId;
    private Long    childRevisionId;
    private Integer quantity;
    private String  positionNumber;

    // BMK in-context fields
    private String referenceDesignator;
    private String bmk;
}