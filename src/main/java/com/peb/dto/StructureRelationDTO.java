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

    // Variant / 150% BOM fields
    private String relationType;       // ALWAYS | OPTIONAL | VARIANT_SPECIFIC | ALTERNATIVE
    private String configRule;         // z.B. "ENGINE_TYPE=V8 AND COLOR=RED"
    private String alternativeGroup;   // z.B. "KUEHLER_GRUPPE"
    private Integer alternativePriority; // lower value = higher priority
}