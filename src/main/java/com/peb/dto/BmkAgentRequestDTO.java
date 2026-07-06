// src/main/java/com/peb/dto/BmkAgentRequestDTO.java
package com.peb.dto;

import lombok.Data;

/**
 * Request DTO für BMK-Generierung.
 * Erweiterung: useAiAgent Flag für Vergleich Alt vs. KI.
 */
@Data
public class BmkAgentRequestDTO {

 /** Object-ID des Root-Objekts (z.B. "MA00000001") */
 private String rootObjectId;

 /** Revision-ID der Root-Revision */
 private Long rootRevisionId;

 /**
  * Flag: true → Gemini Agent nutzen
  *        false → bewährte In-Process Logik (bestehend)
  *
  * Default: false (sicherer Fallback)
  */
 private boolean useAiAgent = false;
}