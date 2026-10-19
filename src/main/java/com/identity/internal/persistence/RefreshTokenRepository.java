package com.identity.internal.persistence;

import com.identity.internal.domain.RefreshToken;
import com.identity.internal.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUser(User user);

    List<RefreshToken> findAllByUserAndRevokedAtIsNull(User user);

    void deleteAllByExpiresAtBefore(Instant time);
}
