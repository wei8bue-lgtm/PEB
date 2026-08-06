// src/main/java/com/peb/dto/BmkExportDTO.java
package com.peb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Export-DTO für die BMK-Konfiguration eines Strukturbaums.
 *
 * Flache Repräsentation aller Verbauungspositionen (StructureRelations)
 * unterhalb eines Root-Objekts – inkl. des generierten, vollständigen BMK
 * sowie der Generierungs-Metadaten. Gedacht für maschinelle
 * Weiterverarbeitung (Excel, Skripte, Integrationen).
 *
 * Wird von GET /api/bmk/export/{rootObjectId} geliefert.
 */
@Data
@Builder
public class BmkExportDTO {

    @JsonProperty("rootObjectId")
    private String rootObjectId;

    @JsonProperty("rootRevisionId")
    private Long rootRevisionId;

    /** BMK-Norm, nach der generiert wird. */
    @JsonProperty("norm")
    private String norm;

    /** Zeitpunkt des Exports. */
    @JsonProperty("exportedAt")
    private LocalDateTime exportedAt;

    /** Anzahl der exportierten Relationen. */
    @JsonProperty("relationCount")
    private int relationCount;

    @JsonProperty("relations")
    private List<Entry> relations;

    /**
     * Einzelne Verbauungsposition mit vollständiger BMK-Konfiguration.
     */
    @Data
    @Builder
    public static class Entry {

        @JsonProperty("relationId")
        private Long relationId;

        @JsonProperty("parentObjectId")
        private String parentObjectId;

        @JsonProperty("childObjectId")
        private String childObjectId;

        /** Objekttyp-Prefix z.B. "MA", "SY", "PT". */
        @JsonProperty("childObjectType")
        private String childObjectType;

        @JsonProperty("quantity")
        private Integer quantity;

        @JsonProperty("positionNumber")
        private String positionNumber;

        /** Vom User eingegebener Teil-BMK, z.B. "=102" oder "-B301A". */
        @JsonProperty("referenceDesignator")
        private String referenceDesignator;

        /** Vollständiges, generiertes BMK, z.B. "=102+M-B301A". */
        @JsonProperty("bmk")
        private String bmk;

        /** Akkumulierter BMK-Pfad des Elternknotens (leer für Root-Kinder). */
        @JsonProperty("parentBmkPath")
        private String parentBmkPath;

        /** Tiefe im Baum (erste Ebene = 1, ...). */
        @JsonProperty("depth")
        private int depth;

        @JsonProperty("bmkGeneratedAt")
        private LocalDateTime bmkGeneratedAt;

        @JsonProperty("bmkGeneratedBy")
        private String bmkGeneratedBy;
    }
}
