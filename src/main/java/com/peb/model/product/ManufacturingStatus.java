// src/main/java/com/peb/model/product/ManufacturingStatus.java
package com.peb.model.product;

public enum ManufacturingStatus {

    WORKING("Working", "🚩", "#6c757d"),
    RELEASED("Released", "✅", "#155724"),
    OBSOLETE("Obsolete", "🔴", "#721c24");

    private final String displayName;
    private final String icon;
    private final String color;

    ManufacturingStatus(String displayName,
                        String icon,
                        String color) {
        this.displayName = displayName;
        this.icon        = icon;
        this.color       = color;
    }

    public String getDisplayName() { return displayName; }
    public String getIcon()        { return icon; }
    public String getColor()       { return color; }
}