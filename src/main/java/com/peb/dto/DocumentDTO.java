package com.peb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for Document creation from a template
 */
@Data
public class DocumentDTO {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 100)
    private String department;

    @Size(max = 1000)
    private String description;

    /** Wert, der {{Attribute1}} im Template ersetzt */
    @Size(max = 500)
    private String attribute1;

    /** Wert, der {{Attribute2}} im Template ersetzt */
    @Size(max = 500)
    private String attribute2;

    /** ID des verwendeten Document Templates */
    @NotBlank
    private String templateId;
}
