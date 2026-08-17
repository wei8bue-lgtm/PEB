package com.peb.util;

/**
 * Distinguishes a standard (100%) BOM from a 150% superset BOM
 * that carries all possible variants and options.
 */
public enum BomType {

    /** Regular, product-specific BOM – every item is always used. */
    HUNDRED_PERCENT("100%", "Standard BOM"),

    /** Superset BOM that contains all variants / options.
     *  Individual items are guarded by configuration rules.     */
    SUPER_BOM_150("150%", "150% Superset BOM (Variant BOM)");

    private final String code;
    private final String displayName;

    BomType(String code, String displayName) {
        this.code        = code;
        this.displayName = displayName;
    }

    public String getCode()        { return code; }
    public String getDisplayName() { return displayName; }
}
