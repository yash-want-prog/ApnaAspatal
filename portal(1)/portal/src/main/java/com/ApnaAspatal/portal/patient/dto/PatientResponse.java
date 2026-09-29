package com.ApnaAspatal.portal.patient.dto;

import java.time.LocalDate;

/**
 * Outgoing body for the patient endpoints.
 *
 * <p>The public shape of a patient, decided here rather than by the database.
 * Fields added to the {@code Patient} entity do not appear in API responses
 * unless they are added to this record as well - which is the entire point.
 */
public record PatientResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        LocalDate dateOfBirth) {
}
