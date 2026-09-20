package com.ApnaAspatal.portal.patient.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Incoming body for {@code POST /api/patients}.
 *
 * <p>Deliberately has no {@code id}: the database assigns it, so a client must
 * not be able to propose one. This type describes the API contract and is
 * independent of how {@code Patient} is stored.
 *
 * <p>The constraints below describe what a client may send. They are not the
 * same thing as the database constraints on the {@code patients} table, which
 * describe what may be stored.
 */
public record PatientRequest(

        @NotBlank(message = "fullName is required")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @NotBlank(message = "phone is required")
        String phone,

        @NotNull(message = "dateOfBirth is required")
        LocalDate dateOfBirth) {
}
