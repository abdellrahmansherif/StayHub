package com.hostverification.internal.application;

import com.common.exception.BadRequestException;
import com.common.exception.ConflictException;
import com.common.exception.NotFoundException;
import com.hostverification.internal.config.AdminHostVerificationProperties;
import com.hostverification.internal.domain.CorrectionField;
import com.hostverification.internal.domain.HostVerification;
import com.hostverification.internal.domain.ReviewDecision;
import com.hostverification.internal.domain.VerificationReview;
import com.hostverification.internal.domain.VerificationStatus;
import com.hostverification.internal.persistence.AdminFaceMatchAttemptRepository;
import com.hostverification.internal.persistence.AdminHostVerificationRepository;
import com.hostverification.internal.persistence.VerificationReviewRepository;
import com.hostverification.internal.web.dto.AdminDecisionRequest;
import com.hostverification.internal.web.dto.AdminDecisionResponse;
import com.hostverification.internal.web.dto.AdminVerificationDetailResponse;
import com.hostverification.internal.web.dto.AdminVerificationPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminHostVerificationServiceTest {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    @Mock private AdminHostVerificationRepository verifications;
    @Mock private AdminFaceMatchAttemptRepository faceMatches;
    @Mock private VerificationReviewRepository reviews;
    @Mock private PrivateImageStorage storage;
    @Mock private AdminHostVerificationProperties properties;

    @InjectMocks
    private AdminHostVerificationService service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getDefaultPageSize()).thenReturn(DEFAULT_PAGE_SIZE);
        lenient().when(properties.getMaxPageSize()).thenReturn(MAX_PAGE_SIZE);
    }

    // =========================================================
    // list()
    // =========================================================

    @Nested
    @DisplayName("list()")
    class ListTests {

        private final Sort expectedSort = Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.asc("id"));

        private Page<HostVerification> emptyPage() {
            return new PageImpl<>(List.of(), PageRequest.of(0, DEFAULT_PAGE_SIZE), 0);
        }

        @Test
        @DisplayName("uses page 0, the default size and newest-first sort when nothing is sent")
        void usesDefaults() {
            when(verifications.findAll(any(Pageable.class))).thenReturn(emptyPage());

            service.list(null, null, null, null);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(verifications).findAll(captor.capture());
            Pageable pageable = captor.getValue();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
            assertThat(pageable.getSort()).isEqualTo(expectedSort);
        }

        @Test
        @DisplayName("sort parameter is ignored: order is always submittedAt desc, id asc")
        void sortIsAlwaysFixed() {
            when(verifications.findAll(any(Pageable.class))).thenReturn(emptyPage());

            service.list(null, 0, 10, "anything,asc");

            verify(verifications).findAll(PageRequest.of(0, 10, expectedSort));
        }

        @Test
        @DisplayName("blank status behaves like no status filter")
        void blankStatusMeansNoFilter() {
            when(verifications.findAll(any(Pageable.class))).thenReturn(emptyPage());

            service.list("   ", null, null, null);

            verify(verifications).findAll(any(Pageable.class));
            verify(verifications, never()).findAllByStatus(any(), any());
        }

        @Test
        @DisplayName("status filter is case-insensitive and trimmed")
        void filtersByStatus() {
            when(verifications.findAllByStatus(any(VerificationStatus.class), any(Pageable.class)))
                    .thenReturn(emptyPage());

            service.list("  pending ", 0, 10, null);

            verify(verifications).findAllByStatus(VerificationStatus.PENDING, PageRequest.of(0, 10, expectedSort));
            verify(verifications, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("maps each verification into a summary")
        void mapsContent() {
            HostVerification v = mock(HostVerification.class);
            when(v.getId()).thenReturn(UUID.randomUUID());
            when(v.getUserId()).thenReturn(UUID.randomUUID());
            when(v.getFullName()).thenReturn("Ali Mohamed");
            when(v.getStatus()).thenReturn(VerificationStatus.PENDING);
            when(v.getSubmittedAt()).thenReturn(Instant.now());
            Page<HostVerification> page = new PageImpl<>(List.of(v), PageRequest.of(0, 10), 1);
            when(verifications.findAll(any(Pageable.class))).thenReturn(page);

            AdminVerificationPageResponse response = service.list(null, 0, 10, null);

            assertThat(response.content()).hasSize(1);
        }

        @Test
        @DisplayName("invalid status -> BadRequestException")
        void invalidStatus() {
            assertThatThrownBy(() -> service.list("NOT_A_STATUS", 0, 10, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid status");
        }

        @Test
        @DisplayName("negative page -> BadRequestException")
        void negativePage() {
            assertThatThrownBy(() -> service.list(null, -1, 10, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("page must be 0 or greater");
        }

        @Test
        @DisplayName("size below 1 -> BadRequestException")
        void sizeTooSmall() {
            assertThatThrownBy(() -> service.list(null, 0, 0, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        @Test
        @DisplayName("size above the configured max -> BadRequestException")
        void sizeTooLarge() {
            assertThatThrownBy(() -> service.list(null, 0, MAX_PAGE_SIZE + 1, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        @Test
        @DisplayName("size equal to the max is allowed")
        void sizeAtMaxIsAllowed() {
            when(verifications.findAll(any(Pageable.class))).thenReturn(emptyPage());

            service.list(null, 0, MAX_PAGE_SIZE, null);

            verify(verifications).findAll(PageRequest.of(0, MAX_PAGE_SIZE, expectedSort));
        }
    }

    // =========================================================
    // get()
    // =========================================================

    @Nested
    @DisplayName("get()")
    class GetTests {

        private final UUID id = UUID.randomUUID();

        private HostVerification verificationWith(VerificationStatus status) {
            HostVerification v = mock(HostVerification.class);
            lenient().when(v.getId()).thenReturn(id);
            lenient().when(v.getStatus()).thenReturn(status);
            lenient().when(v.getClarificationMessage()).thenReturn("Please re-upload your ID");
            return v;
        }

        @Test
        @DisplayName("unknown id -> NotFoundException")
        void notFound() {
            when(verifications.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.get(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Verification not found");
        }

        @Test
        @DisplayName("builds signed document URLs through the storage")
        void usesStorageForDocuments() {
            HostVerification v = verificationWith(VerificationStatus.PENDING);
            when(v.getIdFrontKey()).thenReturn("front-key");
            when(v.getIdBackKey()).thenReturn("back-key");
            when(v.getSelfiePhotoKey()).thenReturn("selfie-key");
            when(verifications.findById(id)).thenReturn(Optional.of(v));
            when(faceMatches.findFirstByVerificationIdOrderByCreatedAtDesc(id)).thenReturn(Optional.empty());

            service.get(id);

            verify(storage).urlFor("front-key");
            verify(storage).urlFor("back-key");
            verify(storage).urlFor("selfie-key");
        }

        @Test
        @DisplayName("latestFaceMatch is null when there is no face match attempt")
        void noFaceMatch() {
            HostVerification v = verificationWith(VerificationStatus.PENDING);
            when(verifications.findById(id)).thenReturn(Optional.of(v));
            when(faceMatches.findFirstByVerificationIdOrderByCreatedAtDesc(id)).thenReturn(Optional.empty());

            AdminVerificationDetailResponse response = service.get(id);

            assertThat(response.latestFaceMatch()).isNull();
        }

        @Test
        @DisplayName("clarification message is returned only when status is NEEDS_CLARIFICATION")
        void clarificationMessageWhenNeedsClarification() {
            HostVerification v = verificationWith(VerificationStatus.NEEDS_CLARIFICATION);
            when(verifications.findById(id)).thenReturn(Optional.of(v));
            when(faceMatches.findFirstByVerificationIdOrderByCreatedAtDesc(id)).thenReturn(Optional.empty());

            AdminVerificationDetailResponse response = service.get(id);

            assertThat(response.clarificationMessage()).isEqualTo("Please re-upload your ID");
        }

        @Test
        @DisplayName("clarification message is hidden for any other status")
        void clarificationMessageHiddenOtherwise() {
            HostVerification v = verificationWith(VerificationStatus.PENDING);
            when(verifications.findById(id)).thenReturn(Optional.of(v));
            when(faceMatches.findFirstByVerificationIdOrderByCreatedAtDesc(id)).thenReturn(Optional.empty());

            AdminVerificationDetailResponse response = service.get(id);

            assertThat(response.clarificationMessage()).isNull();
        }
    }

    // =========================================================
    // decide()
    // =========================================================

    @Nested
    @DisplayName("decide()")
    class DecideTests {

        private final UUID adminId = UUID.randomUUID();
        private final UUID id = UUID.randomUUID();

        /** Single place to adjust if your AdminDecisionRequest has a different component order. */
        private AdminDecisionRequest request(String decision, String reason,
                                             Boolean retryAllowed, List<String> corrections) {
            return new AdminDecisionRequest(decision, reason, retryAllowed, corrections);
        }

        private HostVerification stubVerification(VerificationStatus status) {
            HostVerification v = mock(HostVerification.class);
            when(v.getStatus()).thenReturn(status);
            lenient().when(v.getId()).thenReturn(id);
            when(verifications.findByIdForUpdate(id)).thenReturn(Optional.of(v));
            return v;
        }

        private VerificationReview savedReview() {
            ArgumentCaptor<VerificationReview> captor = ArgumentCaptor.forClass(VerificationReview.class);
            verify(reviews).save(captor.capture());
            return captor.getValue();
        }

        // ---------- validation (nothing touches the DB) ----------

        @Test
        @DisplayName("null or blank decision -> 'decision is required'")
        void decisionRequired() {
            assertThatThrownBy(() -> service.decide(adminId, id, request(null, null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("decision is required");
            assertThatThrownBy(() -> service.decide(adminId, id, request("   ", null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("decision is required");

            verifyNoInteractions(verifications, reviews);
        }

        @Test
        @DisplayName("unknown decision -> BadRequestException")
        void invalidDecision() {
            assertThatThrownBy(() -> service.decide(adminId, id, request("MAYBE", null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("decision must be APPROVED, REJECTED or NEEDS_CLARIFICATION");

            verifyNoInteractions(verifications, reviews);
        }

        @Test
        @DisplayName("reason longer than 1000 characters -> BadRequestException")
        void reasonTooLong() {
            String tooLong = "a".repeat(1001);

            assertThatThrownBy(() -> service.decide(adminId, id, request("REJECTED", tooLong, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("reason must not exceed 1000 characters");

            verifyNoInteractions(verifications, reviews);
        }

        @Test
        @DisplayName("reason of exactly 1000 characters is accepted")
        void reasonAtLimitAccepted() {
            stubVerification(VerificationStatus.PENDING);

            service.decide(adminId, id, request("REJECTED", "a".repeat(1000), null, null));

            assertThat(savedReview().getReason()).hasSize(1000);
        }

        @Test
        @DisplayName("REJECTED without a reason -> BadRequestException")
        void rejectedNeedsReason() {
            assertThatThrownBy(() -> service.decide(adminId, id, request("REJECTED", null, null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("reason is required when decision is REJECTED");

            verifyNoInteractions(verifications, reviews);
        }

        @Test
        @DisplayName("a whitespace-only reason counts as no reason")
        void whitespaceReasonCountsAsMissing() {
            assertThatThrownBy(() -> service.decide(adminId, id, request("REJECTED", "    ", null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("reason is required when decision is REJECTED");
        }

        @Test
        @DisplayName("NEEDS_CLARIFICATION without a reason -> BadRequestException")
        void clarificationNeedsReason() {
            assertThatThrownBy(() -> service.decide(adminId, id,
                    request("NEEDS_CLARIFICATION", null, null, List.of("ID_FRONT"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("reason is required when decision is NEEDS_CLARIFICATION");
        }

        @Test
        @DisplayName("NEEDS_CLARIFICATION with null or empty corrections -> BadRequestException")
        void clarificationNeedsCorrections() {
            assertThatThrownBy(() -> service.decide(adminId, id,
                    request("NEEDS_CLARIFICATION", "blurry", null, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("requiredCorrections is required when decision is NEEDS_CLARIFICATION");
            assertThatThrownBy(() -> service.decide(adminId, id,
                    request("NEEDS_CLARIFICATION", "blurry", null, List.of())))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("requiredCorrections is required when decision is NEEDS_CLARIFICATION");

            verifyNoInteractions(verifications, reviews);
        }

        @Test
        @DisplayName("blank item inside requiredCorrections -> BadRequestException")
        void blankCorrection() {
            assertThatThrownBy(() -> service.decide(adminId, id,
                    request("NEEDS_CLARIFICATION", "blurry", null, Arrays.asList("ID_FRONT", " "))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("requiredCorrections must not contain empty values");
        }

        @Test
        @DisplayName("unknown correction field -> BadRequestException listing the allowed values")
        void invalidCorrection() {
            assertThatThrownBy(() -> service.decide(adminId, id,
                    request("NEEDS_CLARIFICATION", "blurry", null, List.of("PASSPORT"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid correction 'PASSPORT'");
        }

        // ---------- lookup / state checks ----------

        @Test
        @DisplayName("unknown verification -> NotFoundException")
        void verificationNotFound() {
            when(verifications.findByIdForUpdate(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.decide(adminId, id, request("APPROVED", null, null, null)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Verification not found");

            verifyNoInteractions(reviews);
        }

        @Test
        @DisplayName("already APPROVED -> ConflictException")
        void alreadyApproved() {
            stubVerification(VerificationStatus.APPROVED);

            assertThatThrownBy(() -> service.decide(adminId, id, request("REJECTED", "late", null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Verification has already been approved");

            verifyNoInteractions(reviews);
        }

        @Test
        @DisplayName("already REJECTED -> ConflictException")
        void alreadyRejected() {
            stubVerification(VerificationStatus.REJECTED);

            assertThatThrownBy(() -> service.decide(adminId, id, request("APPROVED", null, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Verification has already been rejected");

            verifyNoInteractions(reviews);
        }

        // ---------- successful decisions ----------

        @Test
        @DisplayName("APPROVED: sets status, clears the clarification message and drops any reason")
        void approve() {
            HostVerification v = stubVerification(VerificationStatus.PENDING);

            AdminDecisionResponse response =
                    service.decide(adminId, id, request("approved", "ignored text", true, null));

            verify(v).setStatus(VerificationStatus.APPROVED);
            verify(v).setClarificationMessage(null);
            assertThat(response.status()).isEqualTo(VerificationStatus.APPROVED);

            VerificationReview review = savedReview();
            assertThat(review.getDecision()).isEqualTo(ReviewDecision.APPROVED);
            assertThat(review.getReason()).isNull();
            assertThat(review.getRetryAllowed()).isNull();
            assertThat(review.getAdminId()).isEqualTo(adminId);
            assertThat(review.getDecidedAt()).isNotNull();
            assertThat(review.getRequiredCorrections()).isEmpty();
        }

        @Test
        @DisplayName("REJECTED: trims the reason and defaults retryAllowed to true")
        void rejectDefaultsRetryAllowed() {
            HostVerification v = stubVerification(VerificationStatus.PENDING);

            AdminDecisionResponse response =
                    service.decide(adminId, id, request("REJECTED", "  blurry photo  ", null, null));

            verify(v).setStatus(VerificationStatus.REJECTED);
            verify(v).setClarificationMessage(null);
            assertThat(response.status()).isEqualTo(VerificationStatus.REJECTED);

            VerificationReview review = savedReview();
            assertThat(review.getReason()).isEqualTo("blurry photo");
            assertThat(review.getRetryAllowed()).isTrue();
        }

        @Test
        @DisplayName("REJECTED: keeps an explicit retryAllowed = false")
        void rejectKeepsExplicitRetryFlag() {
            stubVerification(VerificationStatus.PENDING);

            service.decide(adminId, id, request("REJECTED", "fraud", false, null));

            assertThat(savedReview().getRetryAllowed()).isFalse();
        }

        @Test
        @DisplayName("NEEDS_CLARIFICATION: stores the message, de-duplicates corrections and keeps their order")
        void needsClarification() {
            HostVerification v = stubVerification(VerificationStatus.PENDING);

            AdminDecisionResponse response = service.decide(adminId, id, request(
                    "needs_clarification", "  please re-upload  ", null,
                    List.of("selfie_photo", "ID_FRONT", "SELFIE_PHOTO")));

            verify(v).setStatus(VerificationStatus.NEEDS_CLARIFICATION);
            verify(v).setClarificationMessage("please re-upload");
            assertThat(response.status()).isEqualTo(VerificationStatus.NEEDS_CLARIFICATION);

            VerificationReview review = savedReview();
            assertThat(review.getDecision()).isEqualTo(ReviewDecision.NEEDS_CLARIFICATION);
            assertThat(review.getReason()).isEqualTo("please re-upload");
            assertThat(review.getRetryAllowed()).isNull();
            assertThat(review.getRequiredCorrections())
                    .containsExactly(CorrectionField.SELFIE_PHOTO, CorrectionField.ID_FRONT);
        }

        @Test
        @DisplayName("a verification already in NEEDS_CLARIFICATION can still be decided")
        void canDecideAfterClarification() {
            HostVerification v = stubVerification(VerificationStatus.NEEDS_CLARIFICATION);

            AdminDecisionResponse response =
                    service.decide(adminId, id, request("APPROVED", null, null, null));

            verify(v).setStatus(VerificationStatus.APPROVED);
            assertThat(response.status()).isEqualTo(VerificationStatus.APPROVED);
        }
    }
}