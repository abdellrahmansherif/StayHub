package com.identity.internal.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "RefreshToken is required")
        String refreshToken
) {
}
