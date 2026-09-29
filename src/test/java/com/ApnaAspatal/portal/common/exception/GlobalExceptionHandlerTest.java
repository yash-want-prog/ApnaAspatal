package com.ApnaAspatal.portal.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Failures must not leak internal detail to clients. These cannot all be
 * triggered reliably over HTTP, so the handler is exercised directly.
 */
class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-29T06:30:00Z");

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void unexpectedFailureIsAGeneric500() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("Reason codes for session 7 are 620 characters"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void databaseConstraintIsAConflictWithoutTheDatabaseMessage() {
        // A real PostgreSQL message names the constraint and can include row values.
        ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(new DataIntegrityViolationException(
                "update or delete on table \"patients\" violates foreign key constraint; "
                        + "Key (id)=(12) is still referenced from table \"triage_sessions\""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message())
                .isEqualTo("The request conflicts with existing data")
                .doesNotContain("patients", "Key", "12");
    }

    @Test
    void authenticationFailuresAreA401WithFixedMessagesAndABearerChallenge() {
        ResponseEntity<ApiError> badToken = handler.handleAuthentication(
                new InvalidBearerTokenException("Jwt expired at 2026-09-29T05:30:00Z"));
        ResponseEntity<ApiError> noToken = handler.handleAuthentication(
                new InsufficientAuthenticationException("Full authentication is required"));
        ResponseEntity<ApiError> wrongPassword = handler.handleAuthentication(
                new BadCredentialsException("Bad credentials"));

        assertThat(badToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // The validation detail - here, when the token expired - is not passed on.
        assertThat(badToken.getBody().message()).isEqualTo("Invalid or expired access token");
        assertThat(noToken.getBody().message()).isEqualTo("Authentication required");
        assertThat(wrongPassword.getBody().message()).isEqualTo("Invalid username or password");
        assertThat(badToken.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void errorTimestampComesFromTheApplicationClock() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(response.getBody().timestamp()).isEqualTo(NOW);
    }
}
