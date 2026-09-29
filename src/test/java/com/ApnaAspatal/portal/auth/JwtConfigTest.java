package com.ApnaAspatal.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * A weak or malformed signing key must stop the application at startup: with it,
 * anyone could forge a token for any account.
 */
class JwtConfigTest {

    @Test
    void acceptsA256BitKey() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);

        assertThat(JwtConfig.signingKey(key).getEncoded()).hasSize(32);
    }

    @Test
    void rejectsAKeyShorterThan256Bits() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> JwtConfig.signingKey(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsAKeyThatIsNotBase64() {
        assertThatThrownBy(() -> JwtConfig.signingKey("this is not base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }
}
