package com.identity.internal.persistence;

import com.identity.internal.domain.HostVerification;
import com.identity.internal.domain.VerificationStatus;
import org.springframework.data.domain.Example;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface HostVerificationRepository extends JpaRepository<HostVerification, UUID> {
    // GET /host-verifications/me  >> find the early submitted request for being verified
    Optional<HostVerification> findFirstByUserIdOrderBySubmittedAtDesc(UUID userId);

    // 409 on POST /host-verifications      >> for if exist verified one
    boolean existsByUserIdAndStatusIn(UUID userId, Collection<VerificationStatus> statuses);
}
