package com.ApnaAspatal.portal.triage.question;

/**
 * Thrown when a triage question is requested by an id that does not exist.
 *
 * <p>Mirrors the other not-found exceptions: a client error, mapped to HTTP 404
 * by {@code GlobalExceptionHandler}.
 */
public class TriageQuestionNotFoundException extends RuntimeException {

    public TriageQuestionNotFoundException(Long id) {
        super("Triage question not found with id: " + id);
    }
}
