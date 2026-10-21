package com.identity.internal.exception;

import com.common.exception.UnauthorizedException;

public class RefreshTokenExpiredException extends UnauthorizedException {
   public RefreshTokenExpiredException()
   {
       super("Refresh Token Expired Exception");
   }
}
