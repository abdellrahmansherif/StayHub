package com.identity.internal.persistence;

import com.identity.internal.domain.Role;
import com.identity.internal.domain.User;
import com.identity.internal.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Login and user lookup
    Optional<User> findByEmailIgnoreCase(String email);

    // Check email before registration
    boolean existsByEmailIgnoreCase(String email);


    // Admin: get users by status
    List<User> findAllByUserStatus(UserStatus userStatus);

    // Admin: get users by role
    List<User> findAllByRole(Role role);

    // Useful for checking a specific account state
    boolean existsByEmailIgnoreCaseAndUserStatus(
            String email,
            UserStatus userStatus
    );
}