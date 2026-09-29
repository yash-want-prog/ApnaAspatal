package com.ApnaAspatal.portal.triage;

import com.ApnaAspatal.portal.triage.question.QuestionAnswerType;

/**
 * Thrown when a submitted answer does not fit the type of the question it
 * answers - for example "maybe" to a yes/no question. A client error, mapped to
 * HTTP 400 by {@code GlobalExceptionHandler}.
 */
public class InvalidTriageAnswerException extends RuntimeException {

    public InvalidTriageAnswerException(String questionKey, QuestionAnswerType expectedType) {
        super("Answer to " + questionKey + " must be " + expectedType.expectedFormat());
    }
}
