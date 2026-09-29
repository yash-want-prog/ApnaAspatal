package com.ApnaAspatal.portal.common.exception;

import java.time.Clock;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.ApnaAspatal.portal.auth.InvalidPasswordException;
import com.ApnaAspatal.portal.auth.UsernameTakenException;
import com.ApnaAspatal.portal.patient.PatientNotFoundException;
import com.ApnaAspatal.portal.triage.InvalidTriageAnswerException;
import com.ApnaAspatal.portal.triage.PatientAgeUnavailableException;
import com.ApnaAspatal.portal.triage.QuestionNotAnswerableException;
import com.ApnaAspatal.portal.triage.TriageResultNotFoundException;
import com.ApnaAspatal.portal.triage.TriageSessionNotFoundException;
import com.ApnaAspatal.portal.triage.question.TriageQuestionNotFoundException;

/**
 * Translates exceptions into HTTP responses for every controller in the
 * application.
 *
 * <p>Keeping this in one place means controllers never contain try/catch and
 * never decide status codes for failures - they describe the happy path only.
 *
 * <p>Every failure, including Spring's own (malformed JSON, a wrong method, an
 * unknown URL), is returned as an {@link ApiError}, so clients parse one error
 * shape. Messages never include stack traces, SQL, or internal exception text.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // --- authentication ----------------------------------------------------

    /**
     * No token, a bad token, or wrong credentials at sign-in. The messages are
     * fixed on purpose: token-validation detail (why a signature failed, when a
     * token expired) is useful to an attacker and not to a legitimate client.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        String message;
        if (ex instanceof BadCredentialsException) {
            message = "Invalid username or password";
        } else if (ex instanceof InvalidBearerTokenException) {
            message = "Invalid or expired access token";
        } else {
            message = "Authentication required";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return error(HttpStatus.UNAUTHORIZED, message, headers);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<ApiError> handleUsernameTaken(UsernameTakenException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ApiError> handleInvalidPassword(InvalidPasswordException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // --- domain errors -----------------------------------------------------

    /**
     * Missing and not-yours are the same answer: records belonging to another
     * account are reported as not found, so their existence is not revealed.
     */
    @ExceptionHandler({
            PatientNotFoundException.class,
            TriageSessionNotFoundException.class,
            TriageQuestionNotFoundException.class,
            TriageResultNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidTriageAnswerException.class)
    public ResponseEntity<ApiError> handleInvalidTriageAnswer(InvalidTriageAnswerException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** The question exists, but the session's current state does not allow answering it. */
    @ExceptionHandler(QuestionNotAnswerableException.class)
    public ResponseEntity<ApiError> handleQuestionNotAnswerable(QuestionNotAnswerableException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** The request is valid, but the patient record lacks what evaluation needs. */
    @ExceptionHandler(PatientAgeUnavailableException.class)
    public ResponseEntity<ApiError> handlePatientAgeUnavailable(PatientAgeUnavailableException ex) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    // --- malformed requests ------------------------------------------------

    /**
     * Bean Validation failures on a request body. All violations are reported,
     * sorted so the message is deterministic.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationFailure(MethodArgumentNotValidException ex) {
        String message = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream(),
                        ex.getBindingResult().getGlobalErrors().stream())
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message);
    }

    /** Missing body, invalid JSON, or a value that cannot be bound, such as an unknown enum constant. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    /** A path or query value of the wrong type, such as {@code /api/patients/abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'");
    }

    // --- database constraints ----------------------------------------------

    /**
     * A constraint the database enforced - for example deleting a patient who has
     * triage sessions. Returned as a conflict, never as the raw database message:
     * constraint details can contain patient data, so they are not logged either.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Request rejected by a database constraint ({})", ex.getMostSpecificCause().getClass().getName());
        return error(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    // --- everything else ---------------------------------------------------

    /**
     * Spring's standard web exceptions - unknown URL, unsupported method or media
     * type - carry their own status and keep it. Anything else is unexpected: it is
     * logged in full on the server and reported to the client as a plain 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus known = HttpStatus.resolve(status.value());
            String message = known != null ? known.getReasonPhrase() : "Request could not be processed";
            return error(status, message, errorResponse.getHeaders());
        }

        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ApiError> error(HttpStatusCode status, String message) {
        return error(status, message, HttpHeaders.EMPTY);
    }

    private ResponseEntity<ApiError> error(HttpStatusCode status, String message, HttpHeaders headers) {
        return ResponseEntity.status(status)
                .headers(headers)
                .body(new ApiError(status.value(), message, Instant.now(clock)));
    }
}
