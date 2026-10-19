package com.common.exception;
public class UnauthorizedException extends ApplicationException{
    protected UnauthorizedException(String message) {
        super(message);
    }
}
