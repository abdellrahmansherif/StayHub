package com.identity.internal.exceptions;

import com.common.exception.NotFoundException;

public class EmailNotFoundException extends NotFoundException {
    public EmailNotFoundException()
    {
        super("Email Not Found");
    }
}
