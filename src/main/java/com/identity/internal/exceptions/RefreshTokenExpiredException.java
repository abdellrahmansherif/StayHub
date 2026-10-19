package com.identity.internal.exceptions;

import com.common.exception.UnauthorizedException;

public class RefreshTokenExpiredException extends UnauthorizedException {
   public RefreshTokenExpiredException()
   {
       super("Refresh Token Expired Exception");
   }
}
