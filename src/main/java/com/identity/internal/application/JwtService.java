package com.identity.internal.application;

import com.identity.internal.domain.User;

public interface JwtService {

    String generateAccessToken(User user);

    long getAccessTokenExpirationSeconds();

    String generateRefreshToken();

    long getRefreshTokenExpirationSeconds();
}
