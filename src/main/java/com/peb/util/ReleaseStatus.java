package com.peb.util;

/**
 * Release status enumeration for revisions
 */
public enum ReleaseStatus {
    WORKING("Working", "🚩"),      // Yellow flag
    RELEASED("Released", "🏁"),    // Checkered flag
    OBSOLETE("Obsolete", "🚩");    // Red flag

    private final String displayName;
    private final String icon;

    ReleaseStatus(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }
}