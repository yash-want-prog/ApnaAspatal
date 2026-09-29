package com.ApnaAspatal.portal.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signing and verifying SmartTriage's access tokens: JWTs signed with HMAC-SHA256
 * using one secret key.
 *
 * <p>The key comes only from configuration - {@code smarttriage.security.jwt-secret},
 * normally the {@code JWT_SECRET} environment variable - and is checked at
 * startup, so a missing or weak key stops the application instead of producing
 * forgeable tokens.
 */
@Configuration
public class JwtConfig {

    /** The {@code iss} claim of every token this application issues and accepts. */
    static final String ISSUER = "smarttriage";

    /** HS256 needs a key at least as long as its output: 256 bits. */
    static final int MIN_KEY_BYTES = 32;

    /** Tolerance for clock differences between machines when checking expiry. */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    public JwtEncoder jwtEncoder(@Value("${smarttriage.security.jwt-secret}") String secret) {
        return NimbusJwtEncoder.withSecretKey(signingKey(secret)).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Verifies the signature, then checks the token was issued by SmartTriage and
     * has not expired. Expiry is judged by the application's {@link Clock}, the
     * same one everything else uses.
     */
    @Bean
    public JwtDecoder jwtDecoder(@Value("${smarttriage.security.jwt-secret}") String secret, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(secret))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
        return decoder;
    }

    /**
     * Decodes and checks the configured secret.
     *
     * @param base64Secret a base64-encoded key of at least 32 bytes - for example
     *                     the output of {@code openssl rand -base64 32}
     * @throws IllegalStateException if the secret is not base64 or is too short
     */
    static SecretKey signingKey(String base64Secret) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Secret.strip());
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalStateException("smarttriage.security.jwt-secret must be base64-encoded");
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("smarttriage.security.jwt-secret must decode to at least "
                    + MIN_KEY_BYTES + " bytes (256 bits); it decodes to " + key.length);
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }
}
