package com.ApnaAspatal.portal.triage.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Incoming body for {@code POST /api/triage-sessions}.
 *
 * <p>Only the patient is supplied by the client. Status and timestamps are
 * server-controlled, so they are deliberately absent.
 */
public record TriageSessionRequest(

        @NotNull(message = "patientId is required")
        Long patientId) {
                
}
