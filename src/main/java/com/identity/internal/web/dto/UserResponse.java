package com.identity.internal.web.dto;

import com.identity.internal.domain.Role;
import com.identity.internal.domain.UserStatus;

import java.util.UUID;

public record UserResponse(UUID id,
                               String email,
                               String firstName,
                               String lastName,
                               Role role,
                               UserStatus status) {
}
