package com.hostverification.internal.persistence;

import com.hostverification.internal.domain.VerificationReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VerificationReviewRepository extends JpaRepository<VerificationReview, UUID> {
}
