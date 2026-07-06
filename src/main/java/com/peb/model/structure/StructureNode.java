// src/main/java/com/peb/model/structure/StructureNode.java
package com.peb.model.structure;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Tree node for structure visualization.
 * Extended with BMK in-context attributes.
 */
@Data
public class StructureNode {

    // ── Existing fields ──────────────────────────────────────
    private String  objectId;
    private Long    revisionId;
    private String  name;
    private String  revision;
    private String  releaseStatus;
    private String  objectType;
    private Integer quantity;
    private String  positionNumber;
    private boolean released;
    private List<StructureNode> children = new ArrayList<>();

    // ── NEW: relation identity (needed for inline-edit) ──────
    /** The relation_id of the StructureRelation that placed this
     *  node under its parent. Null for the root node.           */
    private Long relationId;

    // ── NEW: BMK in-context attributes ───────────────────────
    /**
     * Free-text partial BMK entered by the user on the relation,
     * e.g. "-B301A". The AI agent uses this as a hint.
     */
    private String referenceDesignator;

    /**
     * Fully qualified BMK assembled by the AI agent,
     * e.g. "=102+M-B301A".
     */
    private String bmk;
}