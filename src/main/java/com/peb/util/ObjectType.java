// src/main/java/com/peb/util/ObjectType.java
package com.peb.util;

/**
 * Object type enumeration with ID prefixes.
 * Bestehende Typen NICHT verändert – nur PRODUCT_DATA ergänzt.
 */
public enum ObjectType {

    // ── Bestehende Typen (unverändert!) ──────────────────────────
    DESIGN(    "DS", "Design"),
    DRAWING(   "DR", "Drawing"),
    MACHINE(   "MA", "Machine"),
    SYSTEM(    "SY", "System"),
    SUBSYSTEM( "SU", "Subsystem"),
    MODULE(    "MO", "Module"),
    SUBMODULE( "MU", "Submodule"),
    PART(      "PT", "Part"),
    AUXILIARY( "AX", "Auxiliary"),

    // ── NEU ───────────────────────────────────────────────────────
    PRODUCT_DATA("PD", "Product Data");

    // ─────────────────────────────────────────────────────────────

    private final String prefix;
    private final String displayName;

    ObjectType(String prefix, String displayName) {
        this.prefix      = prefix;
        this.displayName = displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Formatiert Sequenznummer zur ID.
     * Beispiel: PRODUCT_DATA, 1 → "PD00000001"
     * Länge: 2 (Prefix) + 8 (Ziffern) = 10 Zeichen
     * → passt zu BaseObject @Column(length = 10)
     */
    public String formatId(long sequence) {
        return prefix + String.format("%08d", sequence);
    }
}