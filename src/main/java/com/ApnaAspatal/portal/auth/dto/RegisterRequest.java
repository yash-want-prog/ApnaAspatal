package com.ApnaAspatal.portal.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Incoming body for {@code POST /api/auth/register}.
 */
public record RegisterRequest(

        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50, message = "username must be 3 to 50 characters")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$",
                message = "username may contain only letters, digits, '.', '_' and '-'")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 12, max = 72, message = "password must be 12 to 72 characters")
        String password) {
}
