package com.ApnaAspatal.portal.triage.dto;

/**
 * Outgoing body for the answer endpoints.
 *
 * <p>Carries the question's key and text alongside the answer so a client can
 * render what was asked without a second request.
 */
public record TriageAnswerResponse(
        Long id,
        Long triageSessionId,
        Long questionId,
        String questionKey,
        String questionText,
        String answer) {
}
