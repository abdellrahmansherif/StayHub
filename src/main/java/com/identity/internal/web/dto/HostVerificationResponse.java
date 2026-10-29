package com.identity.internal.web.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.identity.internal.domain.VerificationStatus;

import java.time.Instant;
import java.util.UUID;
// we will not gonna make a "Host verification request " because we take text or photo not Jason format file
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HostVerificationResponse(
        UUID id,
        VerificationStatus status,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("phone_number") String phoneNumber,
        @JsonProperty("id_front_url") String idFrontUrl,
        @JsonProperty("id_back_url") String idBackUrl,
        @JsonProperty("selfie_photo_url") String selfiePhotoUrl,
        @JsonProperty("clarification_message") String clarificationMessage,
        @JsonProperty("submitted_at") Instant submittedAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
}
