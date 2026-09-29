package com.ApnaAspatal.portal.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ApnaAspatal.portal.auth.dto.TokenResponse;

/**
 * Creating accounts and issuing access tokens.
 */
@Service
public class AuthService {

    /** BCrypt only uses the first 72 bytes of a password; longer ones are refused. */
    static final int MAX_PASSWORD_BYTES = 72;

    private static final String BAD_CREDENTIALS = "Invalid username or password";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration tokenValidity;

    public AuthService(AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${smarttriage.security.token-validity}") Duration tokenValidity) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.tokenValidity = tokenValidity;
    }

    /**
     * Creates an account. The username is stored lower-cased and the password only
     * as a salted hash.
     *
     * @throws UsernameTakenException  if the username is already registered
     * @throws InvalidPasswordException if the password is longer than BCrypt accepts
     */
    @Transactional
    public AppUser register(String username, String password) {
        String normalised = normaliseUsername(username);
        if (exceedsHashLimit(password)) {
            throw new InvalidPasswordException(
                    "password must be at most " + MAX_PASSWORD_BYTES + " bytes when encoded as UTF-8");
        }
        if (appUserRepository.existsByUsername(normalised)) {
            throw new UsernameTakenException(normalised);
        }
        return appUserRepository.save(
                new AppUser(normalised, passwordEncoder.encode(password), LocalDateTime.now(clock)));
    }

    /**
     * Checks the credentials and issues a signed access token for the account.
     *
     * @throws BadCredentialsException if the username or password is wrong - the same
     *                                 failure either way, so accounts cannot be probed
     */
    public TokenResponse issueToken(String username, String password) {
        String normalised = normaliseUsername(username);
        if (exceedsHashLimit(password)) {
            throw new BadCredentialsException(BAD_CREDENTIALS);
        }
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(normalised, password));

        AppUser user = appUserRepository.findByUsername(normalised)
                .orElseThrow(() -> new BadCredentialsException(BAD_CREDENTIALS));

        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(tokenValidity))
                .claim("username", user.getUsername())
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new TokenResponse(token, "Bearer", tokenValidity.toSeconds());
    }

    /** Usernames are case-insensitive: stored and looked up lower-cased. */
    static String normaliseUsername(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean exceedsHashLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
