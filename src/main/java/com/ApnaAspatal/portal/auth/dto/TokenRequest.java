package com.ApnaAspatal.portal.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Incoming body for {@code POST /api/auth/token}.
 */
public record TokenRequest(

        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        String password) {
}
