package com.ApnaAspatal.portal.triage.dto;

/**
 * Outgoing body for {@code GET /api/triage-sessions/{sessionId}/next-question}.
 *
 * <p>{@code answerType} tells the client how to collect the answer.
 */
public record NextQuestionResponse(
        Long questionId,
        String questionKey,
        String questionText,
        String answerType) {
}
