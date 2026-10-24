package com.hostverification.internal.web;

import com.identity.api.CurrentUserFacade;
import com.hostverification.internal.application.HostVerificationService;
import com.hostverification.internal.web.dto.FaceMatchRequest;
import com.hostverification.internal.web.dto.FaceMatchResponse;
import com.hostverification.internal.web.dto.HostVerificationRequest;
import com.hostverification.internal.web.dto.HostVerificationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/host-verifications")
@RequiredArgsConstructor
public class HostVerificationController {
    private final HostVerificationService service;
    private final CurrentUserFacade currentUser;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submit(
            @Valid @ModelAttribute HostVerificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.submit(currentUser.getCurrentUserId(), request));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMine() {
        return ResponseEntity.ok(service.getByUser(currentUser.getCurrentUserId()));
    }

    @PostMapping(value = "/{id}/face-match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FaceMatchResponse> faceMatch(
            @PathVariable UUID id,
            @Valid @ModelAttribute FaceMatchRequest request) {
        return ResponseEntity.ok(service.runFaceMatch(
                currentUser.getCurrentUserId(), currentUser.isAdmin(), id, request));
    }
}
