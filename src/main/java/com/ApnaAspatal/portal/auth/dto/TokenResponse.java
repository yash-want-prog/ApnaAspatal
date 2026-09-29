package com.ApnaAspatal.portal.auth.dto;

/**
 * Outgoing body for {@code POST /api/auth/token}.
 *
 * @param accessToken the signed token to send as {@code Authorization: Bearer <accessToken>}
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   seconds until the token expires
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
