package com.ApnaAspatal.portal.patient;

/**
 * Thrown when a patient is requested by an id that does not exist.
 *
 * <p>This is a client error, not a server fault: the request was well-formed,
 * the resource simply is not there. {@code GlobalExceptionHandler} maps it to
 * HTTP 404.
 *
 * <p>Unchecked on purpose - "not found" can happen at almost any lookup, and
 * forcing every caller to declare or catch it would add noise without adding
 * safety. The handler deals with it once, centrally.
 */
public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(Long id) {
        super("Patient not found with id: " + id);
    }
}
