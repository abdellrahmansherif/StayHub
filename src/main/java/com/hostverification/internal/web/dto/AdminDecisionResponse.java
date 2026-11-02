package com.hostverification.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hostverification.internal.domain.CorrectionField;
import com.hostverification.internal.domain.ReviewDecision;
import com.hostverification.internal.domain.VerificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Response of PATCH /admin/host-verifications/{id}/decision

//   APPROVED:            id, status, decision{type, reason:null, decidedAt}
//   REJECTED:            id, status, decision{type, reason, retryAllowed, decidedAt}
//   NEEDS_CLARIFICATION: id, status, clarificationMessage, requiredCorrections

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminDecisionResponse(
        UUID id,
        VerificationStatus status,
        DecisionDetail decision,
        String clarificationMessage,
        List<CorrectionField> requiredCorrections
) {


    // this is a nested record used for specify the returned details depend on status
    // "reason" is always written (the brief shows "reason": null for APPROVED)
    // "retryAllowed" is only written for REJECTED.

    public record DecisionDetail(
            ReviewDecision type,
            String reason,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean retryAllowed,
            Instant decidedAt
    ) {
    }
}
