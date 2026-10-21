package com.common.exception;
/**
 * Exception thrown when a request contains invalid or unacceptable data.
 * 400 http error
 */
public class BadRequestException extends com.common.exception.ApplicationException {
    /**
     * @param message the message describing why the request is invalid
     */
    protected BadRequestException(String message) {
        super(message);
    }
}
