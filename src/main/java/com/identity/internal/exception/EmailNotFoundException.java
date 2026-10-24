package com.identity.internal.exception;

import com.common.exception.NotFoundException;

public class EmailNotFoundException extends NotFoundException {
    public EmailNotFoundException()
    {
        super("Email Not Found");
    }
}
