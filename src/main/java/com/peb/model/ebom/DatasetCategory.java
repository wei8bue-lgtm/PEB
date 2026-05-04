package com.peb.model.ebom;

/**
 * Dataset category enumeration for eBOM objects
 */
public enum DatasetCategory {
    PDF_DOCUMENT("PDF Document"),
    PDF_DRAWING("PDF Drawing"),
    EXCEL_TABLE("Excel Table"),
    CE_DOCUMENT("CE Document"),
    OTHER("Other");

    private final String displayName;

    DatasetCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}