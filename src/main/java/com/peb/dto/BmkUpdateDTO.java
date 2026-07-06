// src/main/java/com/peb/dto/BmkUpdateDTO.java
package com.peb.dto;

import lombok.Data;

/**
 * Payload für manuelles Überschreiben von BMK / Reference Designator
 * direkt im Structure View.
 */
@Data
public class BmkUpdateDTO {
    private Long   relationId;
    private String referenceDesignator;  // user-editable
    private String bmk;                  // manually correctable
}