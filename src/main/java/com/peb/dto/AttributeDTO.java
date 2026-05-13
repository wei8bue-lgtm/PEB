package com.peb.dto;

import lombok.Data;

import java.util.Map;

/**
 * DTO für Attribut-Operationen
 */
@Data
public class AttributeDTO {

    private String objectId;
    private String attributeName;
    private String attributeValue;

    // Für Bulk-Update mehrerer Attribute gleichzeitig
    private Map<String, String> attributes;
}