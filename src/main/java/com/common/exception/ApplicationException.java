package com.common.exception;
/**
 * Base class for application-specific unchecked exceptions.
 * Other custom exceptions can extend this class to share a common parent.
 */
public class ApplicationException extends RuntimeException{
    /**
     * @param message the message describing the error
     *  Handling errors without tryCatch throw new NotFoundException("User not found");
     */
    protected ApplicationException(String message)
    {
        super(message);
    }
}
