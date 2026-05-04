package com.peb.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Validation utility methods
 */
public class ValidationUtil {

    public static boolean isValidName(String name) {
        return StringUtils.isNotBlank(name) && name.length() <= 256;
    }

    public static boolean isValidDepartment(String department) {
        return department == null || department.length() <= 128;
    }

    public static boolean isValidMass(String mass) {
        return mass == null || (mass.length() <= 8 && mass.matches("\\d+(\\.\\d+)?"));
    }

    public static boolean isValidMaterial(String material) {
        return material == null || material.length() <= 256;
    }

    public static boolean isValidSupplier(String supplier) {
        return supplier == null || supplier.length() <= 256;
    }

    public static boolean isValidRevision(String revision) {
        return StringUtils.isNotBlank(revision) && revision.length() <= 2;
    }
}