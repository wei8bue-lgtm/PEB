package com.peb.exception;

import java.util.List;

/**
 * Raised when an alternative group resolves to multiple matching candidates
 * with the same top priority, making deterministic selection impossible.
 */
public class VariantResolutionConflictException extends IllegalStateException {

    private final String alternativeGroup;
    private final int priority;
    private final List<String> candidates;

    public VariantResolutionConflictException(String alternativeGroup,
                                              int priority,
                                              List<String> candidates) {
        super(buildMessage(alternativeGroup, priority, candidates));
        this.alternativeGroup = alternativeGroup;
        this.priority = priority;
        this.candidates = candidates;
    }

    private static String buildMessage(String alternativeGroup,
                                       int priority,
                                       List<String> candidates) {
        return "Ambiguous alternative group '" + alternativeGroup
                + "': multiple matching nodes share priority " + priority
                + " -> " + String.join(", ", candidates);
    }

    public String getAlternativeGroup() {
        return alternativeGroup;
    }

    public int getPriority() {
        return priority;
    }

    public List<String> getCandidates() {
        return candidates;
    }
}