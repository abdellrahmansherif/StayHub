package com.identity.internal.persistence;

import com.identity.internal.domain.FaceMatchAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FaceMatchAttemptRepository extends JpaRepository<FaceMatchAttempt, UUID> {
}
