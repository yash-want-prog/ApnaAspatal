package com.ApnaAspatal.portal.triage.dto;

import com.ApnaAspatal.portal.triage.SymptomOnset;
import com.ApnaAspatal.portal.triage.SymptomSeverity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Incoming body for {@code POST /api/triage-sessions/{sessionId}/symptoms}.
 *
 * <p>The session is taken from the URL, not the body, so it is deliberately
 * absent here. Sizes mirror the column lengths on {@code Symptom} so that a
 * too-long value is rejected as a 400 rather than failing in the database.
 */
public record SymptomRequest(

        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @NotNull(message = "severity is required")
        SymptomSeverity severity,

        @NotNull(message = "onset is required")
        SymptomOnset onset,

        @Size(max = 60, message = "duration must be at most 60 characters")
        String duration) {
}
