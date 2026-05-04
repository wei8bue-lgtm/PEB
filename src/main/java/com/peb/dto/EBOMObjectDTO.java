package com.peb.dto;

import com.peb.util.ObjectType;
import lombok.Data;

@Data
public class EBOMObjectDTO {
    private ObjectType objectType;
    private String name;
    private String department;
    private String mass;
    private String material;
    private String supplier;
    private String linkedDesignId;
}