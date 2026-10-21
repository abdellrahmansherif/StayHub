package com.identity.internal.application;

import com.identity.internal.domain.RefreshToken;
import com.identity.internal.domain.Role;
import com.identity.internal.domain.User;
import com.identity.internal.domain.UserStatus;
import com.identity.internal.persistence.RefreshTokenRepository;
import com.identity.internal.persistence.UserRepository;
import com.identity.internal.web.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @Mock
    SpringJwtService jwtService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    AuthenticationManager authenticationManager;

    @InjectMocks
    AuthService authService;

    private User makeUser() {
        return User.builder()
                .email("ali@example.com")
                .password("encoded")
                .firstName("Ali")
                .lastName("Mohamed")
                .role(Role.CUSTOMER)
                .userStatus(UserStatus.ACTIVE)
                .build();
    }

    private String sha256(String s) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(d.digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void register() {
        RegisterRequest request = new RegisterRequest("ali@example.com", "pass123", "Ali", "Mohamed");

        when(userRepository.existsByEmailIgnoreCase("ali@example.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("encoded");
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");

        RegisterResponse response = authService.register(request);

        assertNotNull(response);
        verify(userRepository).save(any(User.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login() {
        LoginRequest request = new LoginRequest("ali@example.com", "pass123");

        when(userRepository.findByEmailIgnoreCase("ali@example.com"))
                .thenReturn(Optional.of(makeUser()));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        verify(authenticationManager).authenticate(any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshToken() throws Exception {
        RefreshToken stored = RefreshToken.builder()
                .tokenHash(sha256("refresh-token"))
                .expiresAt(Instant.now().plusSeconds(3600))
                .user(makeUser())
                .build();

        when(refreshTokenRepository.findByTokenHash(sha256("refresh-token")))
                .thenReturn(Optional.of(stored));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("new-access-token");

        RefreshTokenResponse response = authService.refreshToken(new RefreshTokenRequest("refresh-token"));

        assertNotNull(response);
    }
}