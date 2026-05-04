package com.peb.exception;

/**
 * Exception thrown when an object is not found
 */
public class ObjectNotFoundException extends PEBException {
    public ObjectNotFoundException(String message) {
        super(message);
    }
}