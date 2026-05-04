package com.peb.dto;

import lombok.Data;

@Data
public class StructureDTO {
    private String parentId;
    private Long parentRevisionId;
    private String childId;
    private Long childRevisionId;
    private Integer quantity;
    private String positionNumber;
}