package com.common.exception;
/**
 * Exception thrown when a requested resource cannot be found.
 */
public class NotFoundException extends ApplicationException {
    protected NotFoundException(String message) {
        super(message);
    }
}
