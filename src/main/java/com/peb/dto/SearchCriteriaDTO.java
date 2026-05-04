package com.peb.dto;

import lombok.Data;

@Data
public class SearchCriteriaDTO {
    private String attributeName;
    private String searchValue;
    private String objectType;
}