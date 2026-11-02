package com.hostverification.internal.web.dto;

import java.util.List;

// Body of PATCH /admin/host-verifications/{id}/decision
//   APPROVED:            { "decision": "APPROVED" }
//   REJECTED:            { "decision": "REJECTED", "reason": "...", "retryAllowed": true }
//   NEEDS_CLARIFICATION: { "decision": "NEEDS_CLARIFICATION", "reason": "...", "requiredCorrections": ["ID_FRONT"] }

public record AdminDecisionRequest(
        String decision,
        String reason,
        Boolean retryAllowed,
        List<String> requiredCorrections
) {

}
