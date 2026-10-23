package com.common.exception;
/**
 * Exception thrown when a requested resource cannot be found.
 */
public class NotFoundException extends ApplicationException {
    public NotFoundException(String message) {
        super(message);
    }
}
