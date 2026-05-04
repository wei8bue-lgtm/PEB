package com.peb.exception;

/**
 * Exception thrown during workflow operations
 */
public class WorkflowException extends PEBException {
    public WorkflowException(String message) {
        super(message);
    }

    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
    }
}