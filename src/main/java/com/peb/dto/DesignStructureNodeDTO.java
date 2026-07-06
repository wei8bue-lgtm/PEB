package com.peb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignStructureNodeDTO {

    private String  id;
    private String  name;
    private String  department;
    private Long    revisionId;
    private String  revision;
    private String  releaseStatus;
    private String  releaseStatusDisplay;
    private Long    structureId;
    private Integer quantity;
    private String  positionNumber;
    private int     depth;

    @Builder.Default
    private List<DesignStructureNodeDTO> children = new ArrayList<>();

    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    /** Padding in rem für die Einrückung (depth * 1.5rem). */
    public String getPaddingLeft() {
        return (depth * 1.5) + "rem";
    }

    /** CSS-Klasse für die Border-Farbe je Tiefe. */
    public String getDepthClass() {
        return switch (depth) {
            case 0  -> "depth-0";
            case 1  -> "depth-1";
            case 2  -> "depth-2";
            case 3  -> "depth-3";
            default -> "depth-4";
        };
    }
}