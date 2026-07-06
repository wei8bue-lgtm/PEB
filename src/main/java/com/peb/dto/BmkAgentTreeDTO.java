// src/main/java/com/peb/dto/BmkAgentTreeDTO.java
package com.peb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO das den Strukturbaum kompakt für den Gemini Agenten aufbereitet.
 *
 * Enthält nur die für die BMK-Vergabe relevanten Felder –
 * keine internen IDs, keine PLM-spezifischen Details die den
 * Agenten verwirren könnten.
 *
 * Beispiel-Serialisierung:
 * {
 *   "rootObjectId": "MA00000001",
 *   "norm": "N54_D6_DIN_EN_81346-2",
 *   "relations": [
 *     {
 *       "relationId": 42,
 *       "parentObjectId": "MA00000001",
 *       "childObjectId": "SY00000001",
 *       "childObjectType": "SY",
 *       "referenceDesignator": "=102",
 *       "parentBmkPath": "",
 *       "depth": 1
 *     },
 *     ...
 *   ]
 * }
 */
@Data
@Builder
public class BmkAgentTreeDTO {

 @JsonProperty("rootObjectId")
 private String rootObjectId;

 @JsonProperty("norm")
 private String norm;

 @JsonProperty("relations")
 private List<RelationEntry> relations;

 public int getRelationCount() {
     return relations == null ? 0 : relations.size();
 }

 /**
  * Einzelne Verbauungsposition für den Agenten.
  */
 @Data
 @Builder
 public static class RelationEntry {

     /** PK der StructureRelation – wird vom Agent in der Antwort zurückgegeben */
     @JsonProperty("relationId")
     private Long relationId;

     @JsonProperty("parentObjectId")
     private String parentObjectId;

     @JsonProperty("childObjectId")
     private String childObjectId;

     /** Objekttyp-Prefix z.B. "MA", "SY", "PT" – hilft dem Agenten */
     @JsonProperty("childObjectType")
     private String childObjectType;

     /** Vom User eingegebener Teil-BMK, z.B. "=102" oder "+M" */
     @JsonProperty("referenceDesignator")
     private String referenceDesignator;

     /**
      * Akkumulierter BMK-Pfad des Elternknotens.
      * Leer für Root-Kinder.
      * Hilft dem Agenten den Kontext zu verstehen.
      */
     @JsonProperty("parentBmkPath")
     private String parentBmkPath;

     /** Tiefe im Baum (Root = 0, erste Ebene = 1, ...) */
     @JsonProperty("depth")
     private int depth;
 }
}