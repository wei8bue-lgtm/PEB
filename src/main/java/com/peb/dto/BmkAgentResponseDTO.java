// src/main/java/com/peb/dto/BmkAgentResponseDTO.java
package com.peb.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response nach Agenten-Lauf.
 */
@Data
public class BmkAgentResponseDTO {
    private boolean       success;
    private String        message;
    private int           updatedRelations;
    private LocalDateTime triggeredAt;
    private List<String>  warnings;        // z.B. fehlende Reference Designators
}