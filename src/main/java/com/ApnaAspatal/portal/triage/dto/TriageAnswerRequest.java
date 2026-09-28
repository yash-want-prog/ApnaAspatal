package com.ApnaAspatal.portal.triage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Incoming body for {@code POST /api/triage-sessions/{sessionId}/answers}.
 *
 * <p>The session comes from the URL, so only the question and the answer are
 * supplied here.
 */
public record TriageAnswerRequest(

        @NotNull(message = "questionId is required")
        Long questionId,

        @NotBlank(message = "answer is required")
        @Size(max = 500, message = "answer must be at most 500 characters")
        String answer) {
}
