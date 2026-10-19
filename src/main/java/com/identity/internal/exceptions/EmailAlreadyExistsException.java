package com.identity.internal.exceptions;

import com.common.exception.ConflictException;

public class EmailAlreadyExistsException extends ConflictException {

    public EmailAlreadyExistsException() {
        super("Email already exists" );
    }
}
