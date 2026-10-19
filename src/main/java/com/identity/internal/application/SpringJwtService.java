package com.identity.internal.application;

import com.identity.internal.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class SpringJwtService implements JwtService {

    private static final long ACCESS_TOKEN_EXPIRATION_SECONDS = 900;
    private static final long REFRESH_TOKEN_EXPIRATION_SECONDS =
            30L * 24 * 60 * 60;

    @Autowired
    public JwtEncoder jwtEncoder;

    @Override
    public String generateAccessToken(User user) {

        Instant now = Instant.now();
        Instant expiresAt = now.plus(
                ACCESS_TOKEN_EXPIRATION_SECONDS,
                ChronoUnit.SECONDS
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("smart-ticket")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }

    @Override
    public String generateRefreshToken() {

        SecureRandom secureRandom = new SecureRandom();

        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    @Override
    public long getRefreshTokenExpirationSeconds() {
        return REFRESH_TOKEN_EXPIRATION_SECONDS;
    }

    @Override
    public long getAccessTokenExpirationSeconds() {
        return ACCESS_TOKEN_EXPIRATION_SECONDS;
    }
}
