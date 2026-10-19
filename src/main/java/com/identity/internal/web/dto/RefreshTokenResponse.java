package com.identity.internal.web.dto;

public record RefreshTokenResponse (
        String accessToken,
        String tokenType,
        long expiresIn){
}
