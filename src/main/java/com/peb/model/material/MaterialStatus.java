package com.peb.model.material;

/**
 * Lebenszyklus-Status eines Materials (unabhängig vom Revisions-Freigabestatus).
 */
public enum MaterialStatus {
    ACTIVE("Aktiv"),
    UNDER_REVIEW("In Prüfung"),
    OBSOLETE("Ausgelaufen"),
    BLOCKED("Gesperrt");

    private final String displayName;

    MaterialStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
