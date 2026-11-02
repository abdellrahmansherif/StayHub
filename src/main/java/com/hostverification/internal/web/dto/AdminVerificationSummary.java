package com.hostverification.internal.web.dto;

import com.hostverification.internal.domain.VerificationStatus;

import java.time.Instant;
import java.util.UUID;

// البيانات المختصرة لكل طلب في القائمة، من غير التفاصيل والصور

public record AdminVerificationSummary(
        UUID id,
        UUID userId,
        String fullName,
        VerificationStatus status,
        Instant submittedAt
) {
}
