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
import com.hostverification.internal.web.dto.HostStatusResponse;
import com.hostverification.internal.web.dto.HostVerificationRequest;
import com.hostverification.internal.web.dto.HostVerificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostVerificationServiceTest {

    @Mock private HostVerificationRepository verifications;
    @Mock private FaceMatchAttemptRepository attempts;
    @Mock private PrivateImageStorage storage;
    @Mock private FaceMatchClient faceMatchClient;

    @InjectMocks
    private HostVerificationService service;

    private final UUID userId = UUID.randomUUID();

    // ---------- file helpers ----------

    private static final byte[] JPG_BYTES =
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'};
    private static final byte[] PNG_BYTES =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static MockMultipartFile jpg(String field) {
        return new MockMultipartFile(field, field + ".jpg", "image/jpeg", JPG_BYTES);
    }

    private static MockMultipartFile png(String field) {
        return new MockMultipartFile(field, field + ".png", "image/png", PNG_BYTES);
    }

    // =========================================================
    // getStatus()
    // =========================================================

    @Nested
    @DisplayName("getStatus()")
    class GetStatus {

        @Test
        @DisplayName("no verification yet -> NOT_SUBMITTED")
        void notSubmitted() {
            when(verifications.findFirstByUserIdOrderBySubmittedAtDesc(userId)).thenReturn(Optional.empty());

            HostStatusResponse response = service.getStatus(userId);

            assertThat(response.verificationStatus()).isEqualTo("NOT_SUBMITTED");
        }

        @ParameterizedTest
        @EnumSource(VerificationStatus.class)
        @DisplayName("status text mirrors the latest verification's status")
        void mirrorsLatestStatus(VerificationStatus status) {
            HostVerification v = mock(HostVerification.class);
            when(v.getStatus()).thenReturn(status);
            // getClarificationMessage() is only read for NEEDS_CLARIFICATION; unstubbed returns null
            when(verifications.findFirstByUserIdOrderBySubmittedAtDesc(userId)).thenReturn(Optional.of(v));

            HostStatusResponse response = service.getStatus(userId);

            assertThat(response.verificationStatus()).isEqualTo(status.name());
        }
    }

    // =========================================================
    // submit()
    // =========================================================

    @Nested
    @DisplayName("submit()")
    class Submit {

        /** Single place to adjust if HostVerificationRequest has a different component order. */
        private HostVerificationRequest request(String fullName, String phone,
                                                MultipartFile front, MultipartFile back, MultipartFile selfie) {
            return new HostVerificationRequest(fullName, phone, front, back, selfie);
        }

        private HostVerificationRequest validRequest() {
            return request("  Ali Mohamed  ", "  01012345678  ", jpg("id_front"), jpg("id_back"), jpg("selfie_photo"));
        }

        private void stubAllUploads() {
            when(storage.save(any(UUID.class), eq("id_front"), anyString(), any(MultipartFile.class)))
                    .thenReturn("front-key");
            when(storage.save(any(UUID.class), eq("id_back"), anyString(), any(MultipartFile.class)))
                    .thenReturn("back-key");
            when(storage.save(any(UUID.class), eq("selfie"), anyString(), any(MultipartFile.class)))
                    .thenReturn("selfie-key");
        }

        // ---------- validation ----------

        @Test
        @DisplayName("missing or blank full name -> 'full_name is required'")
        void fullNameRequired() {
            assertThatThrownBy(() -> service.submit(userId,
                    request(null, "0101", jpg("a"), jpg("b"), jpg("c"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("full_name is required");
            assertThatThrownBy(() -> service.submit(userId,
                    request("   ", "0101", jpg("a"), jpg("b"), jpg("c"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("full_name is required");

            verifyNoInteractions(verifications, storage);
        }

        @Test
        @DisplayName("missing or blank phone number -> 'phone_number is required'")
        void phoneRequired() {
            assertThatThrownBy(() -> service.submit(userId,
                    request("Ali", null, jpg("a"), jpg("b"), jpg("c"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("phone_number is required");
            assertThatThrownBy(() -> service.submit(userId,
                    request("Ali", " ", jpg("a"), jpg("b"), jpg("c"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("phone_number is required");

            verifyNoInteractions(verifications, storage);
        }

        @Test
        @DisplayName("each image is required (null or empty file)")
        void imagesRequired() {
            MockMultipartFile empty = new MockMultipartFile("x", new byte[0]);

            assertThatThrownBy(() -> service.submit(userId,
                    request("Ali", "0101", null, jpg("b"), jpg("c"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("id_front is required");
            assertThatThrownBy(() -> service.submit(userId,
                    request("Ali", "0101", jpg("a"), empty, jpg("c"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("id_back is required");
            assertThatThrownBy(() -> service.submit(userId,
                    request("Ali", "0101", jpg("a"), jpg("b"), null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("selfie_photo is required");

            verifyNoInteractions(verifications, storage);
        }

        @Test
        @DisplayName("image larger than 5 MB -> BadRequestException")
        void imageTooLarge() {
            MockMultipartFile big = new MockMultipartFile("id_front", "big.jpg", "image/jpeg",
                    new byte[5 * 1024 * 1024 + 1]);

            assertThatThrownBy(() -> service.submit(userId,
                    request("Ali", "0101", big, jpg("b"), jpg("c"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("id_front exceeds 5 MB");
        }

        @Test
        @DisplayName("a file that is not JPG/PNG (checked by magic bytes, not by name) -> BadRequestException")
        void notAnImage() {
            MockMultipartFile fakeJpg = new MockMultipartFile("id_back", "looks-like.jpg", "image/jpeg",
                    "%PDF-1.7 not an image".getBytes());

            assertThatThrownBy(() -> service.submit(userId,
                    request("Ali", "0101", jpg("a"), fakeJpg, jpg("c"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("id_back must be a JPG or PNG image");
        }

        @Test
        @DisplayName("a file shorter than the header -> not an image")
        void tooShortToBeAnImage() {
            MockMultipartFile tiny = new MockMultipartFile("selfie_photo", "t.png", "image/png", new byte[]{1, 2});

            assertThatThrownBy(() -> service.submit(userId,
                    request("Ali", "0101", jpg("a"), jpg("b"), tiny)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("selfie_photo must be a JPG or PNG image");
        }

        @Test
        @DisplayName("an unreadable file -> 'Cannot read <field>'")
        void unreadableFile() throws IOException {
            MultipartFile broken = mock(MultipartFile.class);
            when(broken.getInputStream()).thenThrow(new IOException("disk error"));

            assertThatThrownBy(() -> service.submit(userId,
                    request("Ali", "0101", broken, jpg("b"), jpg("c"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("Cannot read id_front");
        }

        @Test
        @DisplayName("user with a PENDING / NEEDS_CLARIFICATION request -> ConflictException, nothing uploaded")
        void activeRequestExists() {
            when(verifications.existsByUserIdAndStatusIn(eq(userId), any())).thenReturn(true);

            assertThatThrownBy(() -> service.submit(userId, validRequest()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("You already have an active verification request");

            verifyNoInteractions(storage);
            verify(verifications, never()).save(any());
        }

        // ---------- success ----------

        @Test
        @DisplayName("saves a PENDING verification with trimmed text and the three storage keys")
        void savesPendingVerification() {
            when(verifications.existsByUserIdAndStatusIn(eq(userId), any())).thenReturn(false);
            stubAllUploads();
            when(verifications.save(any(HostVerification.class))).thenAnswer(inv -> inv.getArgument(0));

            HostVerificationResponse response = service.submit(userId, validRequest());

            ArgumentCaptor<HostVerification> captor = ArgumentCaptor.forClass(HostVerification.class);
            verify(verifications).save(captor.capture());
            HostVerification saved = captor.getValue();

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getFullName()).isEqualTo("Ali Mohamed");
            assertThat(saved.getPhoneNumber()).isEqualTo("01012345678");
            assertThat(saved.getStatus()).isEqualTo(VerificationStatus.PENDING);
            assertThat(saved.getIdFrontKey()).isEqualTo("front-key");
            assertThat(saved.getIdBackKey()).isEqualTo("back-key");
            assertThat(saved.getSelfiePhotoKey()).isEqualTo("selfie-key");

            assertThat(response).isNotNull();
            verify(storage).urlFor("front-key");
            verify(storage).urlFor("back-key");
            verify(storage).urlFor("selfie-key");
        }

        @Test
        @DisplayName("stores each image with the extension detected from its content")
        void detectsExtensionsFromContent() {
            when(verifications.existsByUserIdAndStatusIn(eq(userId), any())).thenReturn(false);
            stubAllUploads();
            when(verifications.save(any(HostVerification.class))).thenAnswer(inv -> inv.getArgument(0));

            service.submit(userId, request("Ali", "0101", png("id_front"), jpg("id_back"), png("selfie_photo")));

            verify(storage).save(any(UUID.class), eq("id_front"), eq("png"), any(MultipartFile.class));
            verify(storage).save(any(UUID.class), eq("id_back"), eq("jpg"), any(MultipartFile.class));
            verify(storage).save(any(UUID.class), eq("selfie"), eq("png"), any(MultipartFile.class));
        }

        // ---------- cleanup on failure ----------

        @Test
        @DisplayName("if the 2nd upload fails, the 1st is deleted and the error is rethrown")
        void cleansUpWhenSecondUploadFails() {
            when(verifications.existsByUserIdAndStatusIn(eq(userId), any())).thenReturn(false);
            when(storage.save(any(UUID.class), eq("id_front"), anyString(), any(MultipartFile.class)))
                    .thenReturn("front-key");
            when(storage.save(any(UUID.class), eq("id_back"), anyString(), any(MultipartFile.class)))
                    .thenThrow(new IllegalStateException("storage down"));

            assertThatThrownBy(() -> service.submit(userId, validRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("storage down");

            verify(storage).delete("front-key");
            verify(verifications, never()).save(any());
        }

        @Test
        @DisplayName("if saving to the database fails, all three uploaded images are deleted")
        void cleansUpWhenDatabaseSaveFails() {
            when(verifications.existsByUserIdAndStatusIn(eq(userId), any())).thenReturn(false);
            stubAllUploads();
            when(verifications.save(any(HostVerification.class))).thenThrow(new IllegalStateException("db down"));

            assertThatThrownBy(() -> service.submit(userId, validRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("db down");

            verify(storage).delete("front-key");
            verify(storage).delete("back-key");
            verify(storage).delete("selfie-key");
        }

        @Test
        @DisplayName("a failing cleanup does not hide the original error and is kept as suppressed")
        void cleanupFailureIsSuppressed() {
            when(verifications.existsByUserIdAndStatusIn(eq(userId), any())).thenReturn(false);
            stubAllUploads();
            when(verifications.save(any(HostVerification.class))).thenThrow(new IllegalStateException("db down"));
            doThrow(new RuntimeException("cannot delete")).when(storage).delete("front-key");

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                    () -> service.submit(userId, validRequest()));

            assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage("db down");
            assertThat(thrown.getSuppressed()).hasSize(1);
            assertThat(thrown.getSuppressed()[0]).hasMessage("cannot delete");
            // the remaining images are still cleaned up
            verify(storage).delete("back-key");
            verify(storage).delete("selfie-key");
        }
    }

    // =========================================================
    // getByUser()
    // =========================================================

    @Nested
    @DisplayName("getByUser()")
    class GetByUser {

        @Test
        @DisplayName("no verification -> NotFoundException")
        void notFound() {
            when(verifications.findFirstByUserIdOrderBySubmittedAtDesc(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByUser(userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("No verification request found");
        }

        @Test
        @DisplayName("returns the latest verification with signed image URLs")
        void returnsLatest() {
            HostVerification v = HostVerification.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .status(VerificationStatus.PENDING)
                    .idFrontKey("front-key")
                    .idBackKey("back-key")
                    .selfiePhotoKey("selfie-key")
                    .build();
            when(verifications.findFirstByUserIdOrderBySubmittedAtDesc(userId)).thenReturn(Optional.of(v));

            HostVerificationResponse response = service.getByUser(userId);

            assertThat(response).isNotNull();
            verify(storage).urlFor("front-key");
            verify(storage).urlFor("back-key");
            verify(storage).urlFor("selfie-key");
        }
    }

    // =========================================================
    // runFaceMatch()
    // =========================================================

    @Nested
    @DisplayName("runFaceMatch()")
    class RunFaceMatch {

        private final UUID verificationId = UUID.randomUUID();
        private final UUID ownerId = userId;
        private final UUID someoneElse = UUID.randomUUID();

        private HostVerification verificationOwnedBy(UUID owner) {
            return HostVerification.builder()
                    .id(verificationId)
                    .userId(owner)
                    .status(VerificationStatus.PENDING)
                    .build();
        }

        private FaceMatchRequest faceRequest() {
            return new FaceMatchRequest(jpg("id_front"), jpg("selfie_photo"));
        }

        @Test
        @DisplayName("unknown verification -> NotFoundException")
        void notFound() {
            when(verifications.findById(verificationId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.runFaceMatch(userId, false, verificationId, faceRequest()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Verification not found");

            verifyNoInteractions(faceMatchClient);
        }

        @Test
        @DisplayName("a normal user cannot run a face match on someone else's verification (404, not 403)")
        void otherUsersVerificationIsHidden() {
            when(verifications.findById(verificationId)).thenReturn(Optional.of(verificationOwnedBy(someoneElse)));

            assertThatThrownBy(() -> service.runFaceMatch(userId, false, verificationId, faceRequest()))
                    .isInstanceOf(NotFoundException.class);

            verifyNoInteractions(faceMatchClient, attempts);
        }

        @Test
        @DisplayName("an admin can run a face match on any verification")
        void adminCanRunOnAnyVerification() {
            when(verifications.findById(verificationId)).thenReturn(Optional.of(verificationOwnedBy(someoneElse)));
            FaceMatchResponse expected = mock(FaceMatchResponse.class);
            when(faceMatchClient.match(any(MultipartFile.class), any(MultipartFile.class))).thenReturn(expected);

            FaceMatchResponse actual = service.runFaceMatch(userId, true, verificationId, faceRequest());

            assertThat(actual).isSameAs(expected);
        }

        @Test
        @DisplayName("missing id_front -> BadRequestException before calling the AI service")
        void idFrontRequired() {
            when(verifications.findById(verificationId)).thenReturn(Optional.of(verificationOwnedBy(ownerId)));

            assertThatThrownBy(() -> service.runFaceMatch(userId, false, verificationId,
                    new FaceMatchRequest(null, jpg("selfie_photo"))))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("id_front is required");

            verifyNoInteractions(faceMatchClient);
        }

        @Test
        @DisplayName("selfie that is not JPG/PNG -> BadRequestException before calling the AI service")
        void selfieMustBeImage() {
            when(verifications.findById(verificationId)).thenReturn(Optional.of(verificationOwnedBy(ownerId)));
            MockMultipartFile text = new MockMultipartFile("selfie_photo", "s.jpg", "image/jpeg", "hello world".getBytes());

            assertThatThrownBy(() -> service.runFaceMatch(userId, false, verificationId,
                    new FaceMatchRequest(jpg("id_front"), text)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessage("selfie_photo must be a JPG or PNG image");

            verifyNoInteractions(faceMatchClient);
        }

        @Test
        @DisplayName("owner: calls the AI service, stores the attempt and returns the AI result")
        void ownerRunsFaceMatch() {
            HostVerification verification = verificationOwnedBy(ownerId);
            when(verifications.findById(verificationId)).thenReturn(Optional.of(verification));
            FaceMatchResponse expected = mock(FaceMatchResponse.class);
            FaceMatchRequest request = faceRequest();
            when(faceMatchClient.match(request.idFront(), request.selfiePhoto())).thenReturn(expected);

            FaceMatchResponse actual = service.runFaceMatch(userId, false, verificationId, request);

            assertThat(actual).isSameAs(expected);

            ArgumentCaptor<FaceMatchAttempt> captor = ArgumentCaptor.forClass(FaceMatchAttempt.class);
            verify(attempts).save(captor.capture());
            assertThat(captor.getValue().getVerification()).isSameAs(verification);
        }

        @Test
        @DisplayName("a face match never changes the verification status")
        void doesNotChangeStatus() {
            HostVerification verification = verificationOwnedBy(ownerId);
            when(verifications.findById(verificationId)).thenReturn(Optional.of(verification));
            when(faceMatchClient.match(any(MultipartFile.class), any(MultipartFile.class)))
                    .thenReturn(mock(FaceMatchResponse.class));

            service.runFaceMatch(userId, false, verificationId, faceRequest());

            assertThat(verification.getStatus()).isEqualTo(VerificationStatus.PENDING);
            verify(verifications, never()).save(any());
        }

        @Test
        @DisplayName("a second face match for the same verification while one is running -> ConflictException")
        void rejectsConcurrentFaceMatch() {
            when(verifications.findById(verificationId)).thenReturn(Optional.of(verificationOwnedBy(ownerId)));
            AtomicReference<Throwable> nestedFailure = new AtomicReference<>();

            // While the "AI call" is in progress, try to start another one for the same verification
            when(faceMatchClient.match(any(MultipartFile.class), any(MultipartFile.class))).thenAnswer(inv -> {
                try {
                    service.runFaceMatch(userId, false, verificationId, faceRequest());
                } catch (Throwable t) {
                    nestedFailure.set(t);
                }
                return mock(FaceMatchResponse.class);
            });

            service.runFaceMatch(userId, false, verificationId, faceRequest());

            assertThat(nestedFailure.get())
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("A face match is already in progress");
        }

        @Test
        @DisplayName("if the AI call fails, nothing is saved and the verification is unlocked for a retry")
        void releasesLockWhenAiCallFails() {
            when(verifications.findById(verificationId)).thenReturn(Optional.of(verificationOwnedBy(ownerId)));
            when(faceMatchClient.match(any(MultipartFile.class), any(MultipartFile.class)))
                    .thenThrow(new IllegalStateException("AI down"))
                    .thenReturn(mock(FaceMatchResponse.class));

            assertThatThrownBy(() -> service.runFaceMatch(userId, false, verificationId, faceRequest()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("AI down");
            verify(attempts, never()).save(any());

            // second try is NOT blocked with "already in progress"
            FaceMatchResponse retry = service.runFaceMatch(userId, false, verificationId, faceRequest());

            assertThat(retry).isNotNull();
            verify(attempts).save(any(FaceMatchAttempt.class));
        }
    }
}