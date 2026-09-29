package com.ApnaAspatal.portal.auth;

/**
 * Thrown when a new password cannot be accepted - for example one longer than
 * BCrypt can hash, a limit Bean Validation cannot express because it is in bytes,
 * not characters. Mapped to HTTP 400.
 */
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String message) {
        super(message);
    }
}
