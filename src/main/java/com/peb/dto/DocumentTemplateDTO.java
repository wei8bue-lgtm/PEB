package com.peb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for DocumentTemplate creation / editing
 */
@Data
public class DocumentTemplateDTO {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 100)
    private String department;

    @Size(max = 1000)
    private String description;

    @Size(max = 500)
    private String attribute1;

    @Size(max = 500)
    private String attribute2;
}
