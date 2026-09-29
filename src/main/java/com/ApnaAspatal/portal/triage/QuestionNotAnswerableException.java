package com.ApnaAspatal.portal.triage;

import java.util.Optional;

/**
 * Thrown when a question exists but cannot be answered in the session's current
 * state: it has been retired, its branch is not open, or it is not its turn yet.
 *
 * <p>The request is well-formed but conflicts with the session's state, so it
 * maps to HTTP 409 in {@code GlobalExceptionHandler}.
 */
public class QuestionNotAnswerableException extends RuntimeException {

    private QuestionNotAnswerableException(String message) {
        super(message);
    }

    public static QuestionNotAnswerableException inactive(String questionKey) {
        return new QuestionNotAnswerableException(
                "Question " + questionKey + " is no longer asked and cannot be answered");
    }

    public static QuestionNotAnswerableException locked(String questionKey, String dependsOnQuestionKey) {
        return new QuestionNotAnswerableException(
                "Question " + questionKey + " is not on this session's current path: it depends on the answer to "
                        + dependsOnQuestionKey);
    }

    public static QuestionNotAnswerableException notYetDue(String questionKey, Optional<String> currentQuestionKey) {
        return new QuestionNotAnswerableException(currentQuestionKey
                .map(current -> "Question " + questionKey + " cannot be answered yet; the current question is "
                        + current)
                .orElse("Question " + questionKey + " cannot be answered; this session has no question due"));
    }
}
