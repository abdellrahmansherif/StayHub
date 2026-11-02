package com.hostverification.internal.web.dto;
// Response of GET /admin/host-verifications/{id}

import com.hostverification.internal.domain.FaceMatchResult;
import com.hostverification.internal.domain.VerificationStatus;

import java.time.Instant;
import java.util.UUID;
//البيانات والتفاصيل الكاملة لطلب Host Verification معين
// Response of GET /admin/host-verifications/{id}
public record AdminVerificationDetailResponse(
        UUID id,
        UUID userId,
        String fullName,
        String phoneNumber,
        VerificationStatus status,
        Documents documents,
        LatestFaceMatch latestFaceMatch,
        String clarificationMessage,
        Instant submittedAt
) {

    // signed URLs from the private Cloudinary storage.
    public record Documents(
            String idFrontUrl,
            String idBackUrl,
            String selfiePhotoUrl
    ) {
    }

    // The last face match saved in the database.
    public record LatestFaceMatch(
            double similarity,
            FaceMatchResult result
    ) {
    }
}
