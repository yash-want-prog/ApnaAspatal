package com.ApnaAspatal.portal.triage.dto;

import com.ApnaAspatal.portal.triage.SymptomOnset;
import com.ApnaAspatal.portal.triage.SymptomSeverity;

/**
 * Outgoing body for the symptom endpoints.
 *
 * <p>The owning session is represented by its id alone, for the same reasons
 * {@code TriageSessionResponse} carries {@code patientId}.
 */
public record SymptomResponse(
        Long id,
        Long triageSessionId,
        String name,
        SymptomSeverity severity,
        SymptomOnset onset,
        String duration) {
}
