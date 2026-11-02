package com.identity.internal.exception;

import com.common.exception.ConflictException;

public class EmailAlreadyExistsException extends ConflictException {

    public EmailAlreadyExistsException() {
        super("Email already exists" );
    }
}
