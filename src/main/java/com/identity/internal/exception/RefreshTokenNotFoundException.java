package com.identity.internal.exception;

import com.common.exception.NotFoundException;

public class RefreshTokenNotFoundException extends NotFoundException {
    public RefreshTokenNotFoundException()
    {
        super("Refresh Token Not Found");
    }
}
