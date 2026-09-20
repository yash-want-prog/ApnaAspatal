package com.ApnaAspatal.portal.common.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ApnaAspatal.portal.patient.PatientNotFoundException;

/**
 * Translates exceptions into HTTP responses for every controller in the
 * application.
 *
 * <p>Keeping this in one place means controllers never contain try/catch and
 * never decide status codes for failures - they describe the happy path only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PatientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handlePatientNotFound(PatientNotFoundException ex) {
        return new ApiError(HttpStatus.NOT_FOUND.value(), ex.getMessage(), Instant.now());
    }
}
