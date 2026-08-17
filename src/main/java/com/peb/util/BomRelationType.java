package com.peb.util;

/**
 * Describes how a StructureRelation participates in the 150% BOM.
 *
 * ALWAYS           – present in every configuration (default / 100% behaviour)
 * OPTIONAL         – guarded by a configRule; absent when rule evaluates false
 * VARIANT_SPECIFIC – only one of several alternatives is chosen per config
 * ALTERNATIVE      – sibling within an alternative group (use alternativeGroup)
 */
public enum BomRelationType {
    ALWAYS,
    OPTIONAL,
    VARIANT_SPECIFIC,
    ALTERNATIVE
}
