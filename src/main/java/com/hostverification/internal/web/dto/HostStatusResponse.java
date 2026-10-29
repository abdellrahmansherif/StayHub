package com.hostverification.internal.web.dto;

public record HostStatusResponse(
        String verificationStatus,
        boolean canHost,
        boolean canSubmitVerification,
        boolean canResubmit,
        String rejectionReason,
        String clarificationMessage
) {}