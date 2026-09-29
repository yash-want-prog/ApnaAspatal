package com.ApnaAspatal.portal.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The signed-in user making the current request.
 *
 * <p>Services use this to scope every lookup to the caller's own records. It
 * fails closed: with no authenticated user there is no id, so no record can be
 * reached by accident from an unauthenticated code path.
 */
@Component
public class CurrentUser {

    /**
     * The id of the authenticated user - the subject of their access token.
     *
     * @throws AuthenticationCredentialsNotFoundException if no user is authenticated
     */
    public Long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            try {
                return Long.valueOf(token.getToken().getSubject());
            } catch (NumberFormatException invalidSubject) {
                throw new InvalidBearerTokenException("Access token subject is not a user id");
            }
        }
        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
    }
}
