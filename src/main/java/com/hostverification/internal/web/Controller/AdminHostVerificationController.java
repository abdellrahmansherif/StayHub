package com.hostverification.internal.web.Controller;

import com.hostverification.internal.application.AdminHostVerificationService;
import com.hostverification.internal.web.dto.AdminDecisionRequest;
import com.hostverification.internal.web.dto.AdminDecisionResponse;
import com.hostverification.internal.web.dto.AdminVerificationDetailResponse;
import com.hostverification.internal.web.dto.AdminVerificationPageResponse;
import com.identity.api.CurrentUserFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/host-verifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminHostVerificationController {

    private final AdminHostVerificationService service;
    private final CurrentUserFacade currentUser;

    // GET /admin/host-verifications?status=PENDING&page=0&size=10&sort=submittedAt,desc
    @GetMapping
    public ResponseEntity<AdminVerificationPageResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(service.list(status, page, size, sort));
    }

    // GET /admin/host-verifications/{id}
    @GetMapping("/{id}")
    public ResponseEntity<AdminVerificationDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    // PATCH /admin/host-verifications/{id}/decision
    @PatchMapping("/{id}/decision")
    public ResponseEntity<AdminDecisionResponse> decide(
            @PathVariable UUID id,
            @RequestBody AdminDecisionRequest request) {
        return ResponseEntity.ok(service.decide(currentUser.getCurrentUserId(), id, request));
    }
}
