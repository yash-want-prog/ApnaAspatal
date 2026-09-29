package com.ApnaAspatal.portal.triage.dto;

import com.ApnaAspatal.portal.triage.question.QuestionAnswerType;

/**
 * Outgoing body for {@code GET /api/triage-sessions/{sessionId}/next-question}.
 *
 * <p>{@code answerType} tells the client how to collect the answer. Serialised
 * by name, so the JSON is the same as when it was a plain string.
 */
public record NextQuestionResponse(
        Long questionId,
        String questionKey,
        String questionText,
        QuestionAnswerType answerType) {
}
