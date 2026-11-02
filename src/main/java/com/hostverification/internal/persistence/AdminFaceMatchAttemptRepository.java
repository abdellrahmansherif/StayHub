package com.hostverification.internal.persistence;

import com.hostverification.internal.domain.FaceMatchAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminFaceMatchAttemptRepository extends JpaRepository<FaceMatchAttempt, UUID> {

    // "latestFaceMatch" GET /admin/host-verifications/{id}
    Optional<FaceMatchAttempt> findFirstByVerificationIdOrderByCreatedAtDesc(UUID verificationId);
}
