package com.ApnaAspatal.portal.triage.dto;

import java.time.LocalDateTime;

import com.ApnaAspatal.portal.triage.TriageSessionStatus;

/**
 * Outgoing body for the triage session endpoints.
 *
 * <p>The related patient is represented by its id alone. Nesting a patient
 * object here would couple this contract to the patient contract and would force
 * the lazy association to load on every response.
 */
public record TriageSessionResponse(
        Long id,
        Long patientId,
        TriageSessionStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt) {
}
