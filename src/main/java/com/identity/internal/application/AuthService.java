package com.identity.internal.application;

import com.identity.internal.domain.RefreshToken;
import com.identity.internal.domain.Role;
import com.identity.internal.domain.User;
import com.identity.internal.domain.UserStatus;
import com.identity.internal.exception.EmailAlreadyExistsException;
import com.identity.internal.exception.RefreshTokenExpiredException;
import com.identity.internal.exception.RefreshTokenNotFoundException;
import com.identity.internal.persistence.RefreshTokenRepository;
import com.identity.internal.persistence.UserRepository;
import com.identity.internal.web.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@org.springframework.stereotype.Service
public class AuthService {
    @Autowired
    public UserRepository userRepository;

    @Autowired
    public RefreshTokenRepository refreshTokenRepository;

    @Autowired
    public SpringJwtService jwtService;

    @Autowired
    public PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    public RegisterResponse register(RegisterRequest request)
    {
        // Checks whether an account with the same email already exists.
        if(userRepository.existsByEmailIgnoreCase(request.email()))
        {
            throw new EmailAlreadyExistsException();
        }
        // Creates a new user account with an encoded password and the default CUSTOMER role.
        User user= User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .userStatus(UserStatus.ACTIVE)
                .role(Role.CUSTOMER)
                .build();

        // Saves the new user account in the database.
        userRepository.save(user);

        // Converts the saved user entity into a response DTO.
        UserResponse userResponse = toUserResponse(user);

        // Generates an access token for accessing protected API endpoints.
        String accessToken =
                jwtService.generateAccessToken(user);

        // Generates a refresh token for obtaining a new access token later.
        String refreshToken =
                jwtService.generateRefreshToken();

        // Saves the hashed refresh token and its metadata in the database.
        SaveTokenToRepo(refreshToken,user);

        // Returns the tokens, token type, access token expiration time, and user details to the client. for the frontend
        return new RegisterResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds(),
                userResponse
        );
    }

    public LoginResponse login(LoginRequest Request)
    {
        Authentication authentication=authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(Request.email(),Request.password())
        );

        // البحث عن المستخدم في قاعدة البيانات باستخدام الإيميل،
        // مع تجاهل الفرق بين الحروف الكبيرة والصغيرة.
        // لو المستخدم مش موجود، بيتم رمي Exception.
        User user = userRepository
                .findByEmailIgnoreCase(Request.email())
                .orElseThrow(EmailNotFoundException::new);

        UserResponse userResponse = toUserResponse(user);

        String accessToken =
                jwtService.generateAccessToken(user);

        String refreshToken =
                jwtService.generateRefreshToken();

        SaveTokenToRepo(refreshToken, user);

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds(),
                userResponse
        );
    }

    public RefreshTokenResponse refreshToken(RefreshTokenRequest request)
    {
        String tokenHash =
                hashToken(request.refreshToken());

        RefreshToken refreshToken =
                refreshTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(
                                RefreshTokenNotFoundException::new
                        );

        if (refreshToken.getRevokedAt() != null) {
            throw new RefreshTokenNotFoundException();
        }
        if (Instant.now().isAfter(refreshToken.getExpiresAt())) {
            throw new RefreshTokenExpiredException();
        }

        User user = refreshToken.getUser();

        String newAccessToken =
                jwtService.generateAccessToken(user);

        return new RefreshTokenResponse(
                newAccessToken,
                "Bearer",
                jwtService.getRefreshTokenExpirationSeconds()
        );
    }


    public void SaveTokenToRepo(String refreshToken,User user)
    {
        RefreshToken refreshTokenn=RefreshToken.builder()
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(jwtService.getAccessTokenExpirationSeconds()))
                .tokenHash(hashToken(refreshToken))
                .user(user)
                .build();

        refreshTokenRepository.save(refreshTokenn);
    }
    private UserResponse toUserResponse(User user) {

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getUserStatus()
        );
    }
    private String hashToken(String token) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            token.getBytes(StandardCharsets.UTF_8)
                    );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
//comment
}
