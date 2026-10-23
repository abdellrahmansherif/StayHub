package com.identity.internal.web.Service;

import com.common.exception.BadRequestException;
import com.common.exception.ConflictException;
import com.common.exception.NotFoundException;

import com.identity.internal.domain.FaceMatchAttempt;
import com.identity.internal.domain.HostVerification;
import com.identity.internal.domain.VerificationStatus;
import com.identity.internal.persistence.FaceMatchAttemptRepository;
import com.identity.internal.persistence.HostVerificationRepository;
import com.identity.internal.web.dto.FaceMatchResponse;
import com.identity.internal.web.dto.HostVerificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class HostVerificationService {

    private static final Set<VerificationStatus> ACTIVE =
            EnumSet.of(VerificationStatus.PENDING, VerificationStatus.NEEDS_CLARIFICATION);

    private final HostVerificationRepository verifications;
    private final FaceMatchAttemptRepository attempts;
    private final PrivateImageStorage storage;
    private final FaceMatchClient faceMatchClient;

    // Prevents two face matches running for the same request (single instance only;
    // use a DB lock if you run more than one instance)
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    // POST /host-verifications
    @Transactional
    public HostVerificationResponse submit(UUID userId, String fullName, String phoneNumber,
                                           MultipartFile idFront, MultipartFile idBack,
                                           MultipartFile selfiePhoto) {
        if (fullName == null || fullName.isBlank()) {
            throw new BadRequestException("full_name is required");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BadRequestException("phone_number is required");
        }
        String frontExt = imageExtension(idFront, "id_front");
        String backExt = imageExtension(idBack, "id_back");
        String selfieExt = imageExtension(selfiePhoto, "selfie_photo");

        if (verifications.existsByUserIdAndStatusIn(userId, ACTIVE)) {
            throw new ConflictException("You already have an active verification request");
        }

        UUID id = UUID.randomUUID();
        HostVerification entity = HostVerification.builder()
                .id(id)
                .userId(userId)
                .fullName(fullName.trim())
                .phoneNumber(phoneNumber.trim())
                .status(VerificationStatus.PENDING)
                .idFrontKey(storage.save(id, "id_front", frontExt, idFront))
                .idBackKey(storage.save(id, "id_back", backExt, idBack))
                .selfiePhotoKey(storage.save(id, "selfie", selfieExt, selfiePhoto))
                .build();

        return toResponse(verifications.save(entity));
    }

    // GET /host-verifications/me
    @Transactional(readOnly = true)
    public HostVerificationResponse getByUser(UUID userId) {
        return verifications.findFirstByUserIdOrderBySubmittedAtDesc(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("No verification request found"));
    }

    // POST /host-verifications/{id}/face-match
    // Not @Transactional on purpose: no DB transaction should stay open while waiting for the AI.
    public FaceMatchResponse runFaceMatch(UUID userId, boolean admin, UUID verificationId,
                                          MultipartFile idFront, MultipartFile selfiePhoto) {
        HostVerification verification = verifications.findById(verificationId)
                .filter(v -> admin || v.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Verification not found"));

        imageExtension(idFront, "id_front");
        imageExtension(selfiePhoto, "selfie_photo");

        if (!inProgress.add(verificationId)) {
            throw new ConflictException("A face match is already in progress");
        }
        try {
            FaceMatchResponse result = faceMatchClient.match(idFront, selfiePhoto);

            attempts.save(FaceMatchAttempt.builder()
                    .verification(verification)
                    .similarity(result.similarity())
                    .threshold(result.threshold())
                    .result(result.result())
                    .executionTimeSeconds(result.executionTimeSeconds())
                    .build());

            // Important: the status is NOT changed here. Only the admin decides.
            return result;
        } finally {
            inProgress.remove(verificationId);
        }
    }

    // ---------- helpers ----------

    private HostVerificationResponse toResponse(HostVerification v) {
        String message = v.getStatus() == VerificationStatus.NEEDS_CLARIFICATION
                ? v.getClarificationMessage() : null;
        return new HostVerificationResponse(
                v.getId(),
                v.getStatus(),
                v.getFullName(),
                v.getPhoneNumber(),
                storage.urlFor(v.getIdFrontKey()),
                storage.urlFor(v.getIdBackKey()),
                storage.urlFor(v.getSelfiePhotoKey()),
                message,
                v.getSubmittedAt(),
                v.getUpdatedAt()
        );
    }

    /** Checks the file is really a jpg or png (by its first bytes, not the content-type header). */
    private String imageExtension(MultipartFile file, String field) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(field + " is required");
        }
        try (InputStream in = file.getInputStream()) {
            byte[] h = in.readNBytes(8);
            boolean jpg = h.length >= 3
                    && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            boolean png = h.length >= 8
                    && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
            if (jpg) return "jpg";
            if (png) return "png";
        } catch (IOException e) {
            throw new BadRequestException("Cannot read " + field);
        }
        throw new BadRequestException(field + " must be a jpg or png image");
    }
}
