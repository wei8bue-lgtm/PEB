package com.peb.util;

/**
 * Object type enumeration with ID prefixes
 */
public enum ObjectType {
    DESIGN("DS", "Design"),
    DRAWING("DR", "Drawing"),
    MACHINE("MA", "Machine"),
    SYSTEM("SY", "System"),
    SUBSYSTEM("SU", "Subsystem"),
    MODULE("MO", "Module"),
    SUBMODULE("MU", "Submodule"),
    PART("PT", "Part"),
    AUXILIARY("AX", "Auxiliary");

    private final String prefix;
    private final String displayName;

    ObjectType(String prefix, String displayName) {
        this.prefix = prefix;
        this.displayName = displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String formatId(long sequence) {
        return String.format("%s%08d", prefix, sequence);
    }
}