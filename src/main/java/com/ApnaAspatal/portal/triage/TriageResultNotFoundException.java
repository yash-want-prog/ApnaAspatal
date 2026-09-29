package com.ApnaAspatal.portal.triage;

/**
 * Thrown when a session's result is requested before the session has ever been
 * evaluated. Mapped to HTTP 404.
 */
public class TriageResultNotFoundException extends RuntimeException {

    public TriageResultNotFoundException(Long sessionId) {
        super("Triage session " + sessionId + " has not been evaluated");
    }
}
