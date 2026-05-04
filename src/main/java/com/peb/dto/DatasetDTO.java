package com.peb.dto;

import com.peb.model.ebom.DatasetCategory;
import lombok.Data;

@Data
public class DatasetDTO {
    private Long revisionId;
    private String revisionType;
    private String fileName;
    private DatasetCategory category;
}