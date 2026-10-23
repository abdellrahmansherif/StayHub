package com.identity.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.identity.internal.domain.FaceMatchResult;

// Used both as the API response and to read the AI service's reply
public record FaceMatchResponse(
        double similarity,
        @JsonProperty("similarity_percent") double similarityPercent,
        double threshold,
        FaceMatchResult result,
        @JsonProperty("execution_time_seconds") double executionTimeSeconds
) {
}