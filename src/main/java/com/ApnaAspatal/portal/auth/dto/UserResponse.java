package com.ApnaAspatal.portal.auth.dto;

/**
 * Outgoing body for {@code POST /api/auth/register}. Never includes the password
 * or its hash.
 */
public record UserResponse(Long id, String username) {
}
