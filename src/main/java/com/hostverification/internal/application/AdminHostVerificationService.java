package com.hostverification.internal.application;

import com.common.exception.BadRequestException;
import com.common.exception.ConflictException;
import com.common.exception.NotFoundException;
import com.hostverification.internal.config.AdminHostVerificationProperties;
import com.hostverification.internal.domain.*;
import com.hostverification.internal.persistence.AdminFaceMatchAttemptRepository;
import com.hostverification.internal.persistence.AdminHostVerificationRepository;
import com.hostverification.internal.persistence.VerificationReviewRepository;
import com.hostverification.internal.web.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminHostVerificationService {

    private static final int MAX_REASON_LENGTH = 1000;

    private final AdminHostVerificationRepository verifications;
    private final AdminFaceMatchAttemptRepository faceMatches;
    private final VerificationReviewRepository reviews;
    private final PrivateImageStorage storage;
    private final AdminHostVerificationProperties properties;

    // =========================================================
    // 1. LIST   GET /admin/host-verifications?status=&page=&size=&sort=
    // =========================================================

// u just read the hostVerification request cant write on it
    @Transactional(readOnly = true)
    public AdminVerificationPageResponse list(String status, Integer page, Integer size, String sort) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? properties.getDefaultPageSize() : size;

        if (pageNumber < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (pageSize < 1 || pageSize > properties.getMaxPageSize()) {
            throw new BadRequestException(
                    "size must be between 1 and " + properties.getMaxPageSize());
        }

        Pageable pageable = PageRequest.of(pageNumber, pageSize, parseSort(sort));

        Page<HostVerification> result = (status == null || status.isBlank())
                ? verifications.findAll(pageable)
                : verifications.findAllByStatus(parseStatus(status), pageable);

        List<AdminVerificationSummary> content = result.getContent().stream()
                .map(v -> new AdminVerificationSummary(
                        v.getId(),
                        v.getUserId(),
                        v.getFullName(),
                        v.getStatus(),
                        v.getSubmittedAt()))
                .toList();

        return new AdminVerificationPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    // =========================================================
    // 2. DETAILS   GET /admin/host-verifications/{id}
    // =========================================================

    @Transactional(readOnly = true)
    public AdminVerificationDetailResponse get(UUID id) {
        HostVerification v = verifications.findById(id)
                .orElseThrow(() -> new NotFoundException("Verification not found"));


        AdminVerificationDetailResponse.LatestFaceMatch latestFaceMatch =
                faceMatches.findFirstByVerificationIdOrderByCreatedAtDesc(id)
                        .map(a -> new AdminVerificationDetailResponse.LatestFaceMatch(
                                a.getSimilarity(), a.getResult()))
                        .orElse(null);

        AdminVerificationDetailResponse.Documents documents =
                new AdminVerificationDetailResponse.Documents(
                        storage.urlFor(v.getIdFrontKey()),
                        storage.urlFor(v.getIdBackKey()),
                        storage.urlFor(v.getSelfiePhotoKey()));

        String clarificationMessage = v.getStatus() == VerificationStatus.NEEDS_CLARIFICATION
                ? v.getClarificationMessage() : null;

        return new AdminVerificationDetailResponse(
                v.getId(),
                v.getUserId(),
                v.getFullName(),
                v.getPhoneNumber(),
                v.getStatus(),
                documents,
                latestFaceMatch,
                clarificationMessage,
                v.getSubmittedAt()
        );
    }

    // =========================================================
    // 3. DECISION   PATCH /admin/host-verifications/{id}/decision
    // =========================================================

    @Transactional
    public AdminDecisionResponse decide(UUID adminId, UUID id, AdminDecisionRequest request) {
        ReviewDecision decision = parseDecision(request.decision());
//لو مبعتش سبب هيخليه فاضي لو بعت سبب هيشيل المسافات من الاول والاخر بس
        String reason = request.reason() == null ? null : request.reason().trim();
        if (reason != null && reason.isEmpty()) {
            reason = null;
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("reason must not exceed " + MAX_REASON_LENGTH + " characters");
        }
        if (decision != ReviewDecision.APPROVED && reason == null) {
            throw new BadRequestException("reason is required when decision is " + decision);
        }

        Set<CorrectionField> corrections = new LinkedHashSet<>();
        if (decision == ReviewDecision.NEEDS_CLARIFICATION) {
            corrections = parseCorrections(request.requiredCorrections());
            if (corrections.isEmpty()) {
                throw new BadRequestException(
                        "requiredCorrections is required when decision is NEEDS_CLARIFICATION");
            }
        }

        HostVerification verification = verifications.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Verification not found"));

        if (verification.getStatus() == VerificationStatus.APPROVED) {
            throw new ConflictException("Verification has already been approved");
        }
        if (verification.getStatus() == VerificationStatus.REJECTED) {
            throw new ConflictException("Verification has already been rejected");
        }

        // PENDING and NEEDS_CLARIFICATION can still be decided.


        Instant decidedAt = Instant.now();

        // Default for REJECTED when the admin does not send retryAllowed: the host may retry.
        Boolean retryAllowed = null;
        if (decision == ReviewDecision.REJECTED) {
            retryAllowed = request.retryAllowed() == null ? Boolean.TRUE : request.retryAllowed();
        }

        VerificationStatus newStatus = switch (decision) {
            case APPROVED -> VerificationStatus.APPROVED;
            case REJECTED -> VerificationStatus.REJECTED;
            case NEEDS_CLARIFICATION -> VerificationStatus.NEEDS_CLARIFICATION;
        };
        verification.setStatus(newStatus);
        // The existing host side (getStatus / getByUser) shows this message to the host.
        verification.setClarificationMessage(
                decision == ReviewDecision.NEEDS_CLARIFICATION ? reason : null);

        reviews.save(VerificationReview.builder()
                .verification(verification)
                .adminId(adminId)
                .decision(decision)
                .reason(decision == ReviewDecision.APPROVED ? null : reason)
                .retryAllowed(retryAllowed)
                .requiredCorrections(corrections)
                .decidedAt(decidedAt)
                .build());

        if (decision == ReviewDecision.NEEDS_CLARIFICATION) {
            return new AdminDecisionResponse(
                    verification.getId(),
                    newStatus,
                    null,
                    reason,
                    List.copyOf(corrections)
            );
        }

        return new AdminDecisionResponse(
                verification.getId(),
                newStatus,
                new AdminDecisionResponse.DecisionDetail(
                        decision,
                        // if condition is true it must be null for logic
                        decision == ReviewDecision.APPROVED ? null : reason,
                        retryAllowed,
                        decidedAt),
                null,
                null
        );
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private VerificationStatus parseStatus(String value) {
        try {
            return VerificationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Invalid status. Allowed: " + Arrays.toString(VerificationStatus.values()));
        }
    }


    // الترتيب ثابت: الأحدث الأول. "id" ترتيب ثانوي علشان الصفحات تفضل ثابتة.
    private Sort parseSort(String sort) {
        return Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.asc("id"));
    }

    // Converts the "decision" text sent by the admin (for example "approved") into a ReviewDecision enum.
    // Used by decide()
    private ReviewDecision parseDecision(String value) {
        //  Missing or blank ("", "   ") -> 400, because a decision is always required
        if (value == null || value.isBlank()) {
            throw new BadRequestException("decision is required");
        }
        try {
            // 2) trim() removes spaces around the text.
            //    toUpperCase(Locale.ROOT) makes "approved" match APPROVED, and Locale.ROOT

            return ReviewDecision.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 3) valueOf throws this when the text is not one of the enum values (for example "ABC").
            throw new BadRequestException("decision must be APPROVED, REJECTED or NEEDS_CLARIFICATION");
        }
    }

    // Converts the "requiredCorrections" list (for example ["ID_FRONT", "selfie_photo"])
    // Used by decide() only when the decision is NEEDS_CLARIFICATION.
    private Set<CorrectionField> parseCorrections(List<String> values) {
        // A Set removes duplicates; LinkedHashSet keeps the order the admin sent them in
        Set<CorrectionField> result = new LinkedHashSet<>();

        // No list at all -> return an empty set.
        // decide() then rejects it with "requiredCorrections is required"
        if (values == null) {
            return result;
        }

        for (String value : values) {
            // An empty item such as null, "" or "  " inside the list -> 400
            if (value == null || value.isBlank()) {
                throw new BadRequestException("requiredCorrections must not contain empty values");
            }
            try {
                // Same conversion as parseDecision: trim, uppercase, valueOf.
                result.add(CorrectionField.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {

                // and the message lists the allowed values: [ID_FRONT, ID_BACK, SELFIE_PHOTO]
                throw new BadRequestException(
                        "Invalid correction '" + value + "'. Allowed: "
                                + Arrays.toString(CorrectionField.values()));
            }
        }

        return result;
    }
}