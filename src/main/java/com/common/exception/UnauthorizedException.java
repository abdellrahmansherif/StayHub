package com.common.exception;
/**
 * Exception thrown when a request cannot be authenticated.
 */
public class UnauthorizedException extends ApplicationException{
    protected UnauthorizedException(String message) {
        super(message);
    }
}
