package com.peb.model.supplier;

/**
 * Lebenszyklus-Status eines Lieferanten (Stammdatensatz),
 * analog zu com.peb.model.material.MaterialStatus.
 */
public enum SupplierStatus {
    ACTIVE,
    UNDER_REVIEW,
    OBSOLETE,
    BLOCKED
}
