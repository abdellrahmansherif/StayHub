package com.hostverification.internal.application;

import com.common.exception.BadRequestException;
import com.common.exception.ConflictException;
import com.common.exception.NotFoundException;
import com.hostverification.internal.domain.FaceMatchAttempt;
import com.hostverification.internal.domain.HostVerification;
import com.hostverification.internal.domain.VerificationStatus;
import com.hostverification.internal.persistence.FaceMatchAttemptRepository;
import com.hostverification.internal.persistence.HostVerificationRepository;
import com.hostverification.internal.web.dto.FaceMatchRequest;
import com.hostverification.internal.web.dto.FaceMatchResponse;
import com.hostverification.internal.web.dto.HostVerificationRequest;
import com.hostverification.internal.web.dto.HostVerificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class HostVerificationService {
    private static final Set<VerificationStatus> ACTIVE = EnumSet.of(
            VerificationStatus.PENDING,
            VerificationStatus.NEEDS_CLARIFICATION
    );

    private final HostVerificationRepository verifications;
    private final FaceMatchAttemptRepository attempts;
    private final PrivateImageStorage storage;
    private final FaceMatchClient faceMatchClient;

    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    @Transactional
    public HostVerificationResponse submit(UUID userId, HostVerificationRequest request) {
        String fullName = request.fullName();
        String phoneNumber = request.phoneNumber();
        MultipartFile idFront = request.idFront();
        MultipartFile idBack = request.idBack();
        MultipartFile selfiePhoto = request.selfiePhoto();

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
        List<String> uploadedKeys = new ArrayList<>();

        try {
            String frontKey = storage.save(id, "id_front", frontExt, idFront);
            uploadedKeys.add(frontKey);
            String backKey = storage.save(id, "id_back", backExt, idBack);
            uploadedKeys.add(backKey);
            String selfieKey = storage.save(id, "selfie", selfieExt, selfiePhoto);
            uploadedKeys.add(selfieKey);

            HostVerification entity = HostVerification.builder()
                    .id(id)
                    .userId(userId)
                    .fullName(fullName.trim())
                    .phoneNumber(phoneNumber.trim())
                    .status(VerificationStatus.PENDING)
                    .idFrontKey(frontKey)
                    .idBackKey(backKey)
                    .selfiePhotoKey(selfieKey)
                    .build();

            HostVerification saved = verifications.save(entity);
            return toResponse(saved);
        } catch (RuntimeException ex) {
            // Best-effort cleanup of uploaded images.
            for (String key : uploadedKeys) {
                try {
                    storage.delete(key);
                } catch (RuntimeException cleanupEx) {
                    ex.addSuppressed(cleanupEx);
                }
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public HostVerificationResponse getByUser(UUID userId) {
        return verifications.findFirstByUserIdOrderBySubmittedAtDesc(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("No verification request found"));
    }

    public FaceMatchResponse runFaceMatch(
            UUID userId, boolean admin, UUID verificationId, FaceMatchRequest request) {
        HostVerification verification = verifications.findById(verificationId)
                .filter(v -> admin || v.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Verification not found"));

        MultipartFile idFront = request.idFront();
        MultipartFile selfiePhoto = request.selfiePhoto();
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
            // Only an admin changes verification status.
            return result;
        } finally {
            inProgress.remove(verificationId);
        }
    }

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

    private String imageExtension(MultipartFile file, String field) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(field + " is required");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new BadRequestException(field + " exceeds 5 MB");
        }

        try (InputStream in = file.getInputStream()) {
            byte[] h = in.readNBytes(8);
            boolean jpg = h.length >= 3
                    && (h[0] & 0xFF) == 0xFF
                    && (h[1] & 0xFF) == 0xD8
                    && (h[2] & 0xFF) == 0xFF;
            boolean png = h.length >= 8
                    && (h[0] & 0xFF) == 0x89
                    && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                    && (h[4] & 0xFF) == 0x0D
                    && (h[5] & 0xFF) == 0x0A
                    && (h[6] & 0xFF) == 0x1A
                    && (h[7] & 0xFF) == 0x0A;
            if (jpg) return "jpg";
            if (png) return "png";
        } catch (IOException e) {
            throw new BadRequestException("Cannot read " + field);
        }
        throw new BadRequestException(field + " must be a JPG or PNG image");
    }
}
