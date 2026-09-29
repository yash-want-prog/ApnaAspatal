package com.ApnaAspatal.portal.patient.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Incoming body for {@code POST /api/patients}.
 *
 * <p>Deliberately has no {@code id}: the database assigns it, so a client must
 * not be able to propose one. This type describes the API contract and is
 * independent of how {@code Patient} is stored.
 *
 * <p>The constraints below describe what a client may send. They are not the
 * same thing as the database constraints on the {@code patients} table, which
 * describe what may be stored - but the sizes match its {@code varchar(255)}
 * columns, so an over-long value is rejected here as a 400 rather than failing
 * in the database.
 */
public record PatientRequest(

        @NotBlank(message = "fullName is required")
        @Size(max = 255, message = "fullName must be at most 255 characters")
        String fullName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "phone is required")
        @Size(max = 255, message = "phone must be at most 255 characters")
        String phone,

        @NotNull(message = "dateOfBirth is required")
        @PastOrPresent(message = "dateOfBirth must not be in the future")
        LocalDate dateOfBirth) {
}
