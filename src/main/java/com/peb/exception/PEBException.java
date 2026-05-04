package com.peb.exception;

/**
 * Base exception for PEB application
 */
public class PEBException extends RuntimeException {
    public PEBException(String message) {
        super(message);
    }

    public PEBException(String message, Throwable cause) {
        super(message, cause);
    }
}