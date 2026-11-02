package com.common.exception;
/**
 * Exception thrown when a user is not authorized to perform an operation.
 */
public class ForbiddenException extends ApplicationException {
    protected ForbiddenException(String message) {
        super(message);
    }
}
