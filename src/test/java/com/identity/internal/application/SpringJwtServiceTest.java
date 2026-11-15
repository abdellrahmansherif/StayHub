package com.identity.internal.application;

import com.identity.internal.domain.Role;
import com.identity.internal.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringJwtServiceTest {

    @Mock
    private JwtEncoder jwtEncoder;

    @InjectMocks
    private SpringJwtService jwtService;

    @Nested
    @DisplayName("generateAccessToken()")
    class GenerateAccessToken {

        private final UUID userId = UUID.randomUUID();

        private User mockUser() {
            User user = org.mockito.Mockito.mock(User.class);
            when(user.getId()).thenReturn(userId);
            when(user.getEmail()).thenReturn("ali@example.com");
            when(user.getRole()).thenReturn(Role.CUSTOMER);
            return user;
        }

        private Jwt encodedJwt() {
            return Jwt.withTokenValue("encoded-token")
                    .header("alg", "none")
                    .claim("k", "v")
                    .build();
        }

        @Test
        @DisplayName("returns the token value produced by the encoder")
        void returnsEncodedTokenValue() {
            when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(encodedJwt());

            String token = jwtService.generateAccessToken(mockUser());

            assertThat(token).isEqualTo("encoded-token");
        }

        @Test
        @DisplayName("puts issuer, subject, email and role into the claims")
        void buildsExpectedClaims() {
            when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(encodedJwt());

            jwtService.generateAccessToken(mockUser());

            JwtClaimsSet claims = captureClaims();
            assertThat(claims.getClaimAsString("iss")).isEqualTo("smart-ticket");
            assertThat(claims.getSubject()).isEqualTo(userId.toString());
            assertThat(claims.getClaimAsString("email")).isEqualTo("ali@example.com");
            assertThat(claims.getClaimAsString("role")).isEqualTo(Role.CUSTOMER.name());
        }

        @Test
        @DisplayName("sets expiry exactly 900 seconds after issuedAt")
        void setsAccessTokenExpiry() {
            when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(encodedJwt());
            Instant before = Instant.now();

            jwtService.generateAccessToken(mockUser());

            Instant after = Instant.now();
            JwtClaimsSet claims = captureClaims();
            Instant issuedAt = claims.getIssuedAt();
            Instant expiresAt = claims.getExpiresAt();

            assertThat(issuedAt).isBetween(before, after);
            assertThat(Duration.between(issuedAt, expiresAt)).isEqualTo(Duration.ofSeconds(900));
        }

        private JwtClaimsSet captureClaims() {
            ArgumentCaptor<JwtEncoderParameters> captor =
                    ArgumentCaptor.forClass(JwtEncoderParameters.class);
            org.mockito.Mockito.verify(jwtEncoder).encode(captor.capture());
            return captor.getValue().getClaims();
        }
    }

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("is 43 URL-safe Base64 characters with no padding (32 random bytes)")
        void hasExpectedFormat() {
            String token = jwtService.generateRefreshToken();

            assertThat(token).matches("^[A-Za-z0-9_-]{43}$");
        }

        @Test
        @DisplayName("produces a different token on every call")
        void isUnique() {
            Set<String> tokens = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                tokens.add(jwtService.generateRefreshToken());
            }

            assertThat(tokens).hasSize(100);
        }
    }

    @Nested
    @DisplayName("expiration getters")
    class ExpirationGetters {

        @Test
        void accessTokenLivesFifteenMinutes() {
            assertThat(jwtService.getAccessTokenExpirationSeconds()).isEqualTo(900L);
        }

        @Test
        void refreshTokenLivesThirtyDays() {
            assertThat(jwtService.getRefreshTokenExpirationSeconds())
                    .isEqualTo(Duration.ofDays(30).toSeconds());
        }
    }
}