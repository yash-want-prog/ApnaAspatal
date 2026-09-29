package com.ApnaAspatal.portal.auth;

/**
 * Thrown when registering a username that already exists. Mapped to HTTP 409.
 */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("Username " + username + " is already registered");
    }
}
