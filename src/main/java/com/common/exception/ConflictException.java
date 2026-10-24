package com.common.exception;
/**
 * Exception thrown when a request conflicts with the current application state.
 */
public class ConflictException extends ApplicationException {
    /**
     * @param message the message explaining the conflict if anything already exists
     */
    public ConflictException(String message) {
        super(message);
    }
}
