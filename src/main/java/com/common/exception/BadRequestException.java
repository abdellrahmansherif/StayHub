package com.common.exception;

public class BadRequestException extends com.common.exception.ApplicationException {
    protected BadRequestException(String message) {
        super(message);
    }
}
