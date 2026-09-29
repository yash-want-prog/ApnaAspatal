package com.ApnaAspatal.portal.triage;

/**
 * Thrown when a triage session is requested by an id that does not exist.
 *
 * <p>Mirrors {@code PatientNotFoundException}: a client error, mapped to HTTP
 * 404 by {@code GlobalExceptionHandler}.
 */
public class TriageSessionNotFoundException extends RuntimeException {

    public TriageSessionNotFoundException(Long id) {
        super("Triage session not found with id: " + id);
    }
}
