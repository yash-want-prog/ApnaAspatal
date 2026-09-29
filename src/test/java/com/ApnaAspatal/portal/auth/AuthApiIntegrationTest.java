package com.ApnaAspatal.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * Registration, sign-in, and bearer-token validation over HTTP, against the real
 * database and the real security filter chain.
 *
 * <p>Unlike the other integration tests, these use real signed tokens from the
 * token endpoint, so signature, expiry, and issuer checks are all exercised.
 * Every test is rolled back.
 */
@SpringBootTest
@Transactional
class AuthApiIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private Clock clock;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // --- public vs protected -----------------------------------------------

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsRequireATokenAndSayNothingElse() throws Exception {
        mvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        // 401 before anything else: an anonymous caller learns nothing about which ids exist.
        mvc.perform(post("/api/triage-sessions/999999999/evaluation")).andExpect(status().isUnauthorized());
    }

    // --- registration ------------------------------------------------------

    @Test
    void registrationCreatesAnAccountWithoutEverReturningThePassword() throws Exception {
        ResultActions result = register("Asha.Patel", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("asha.patel"))
                .andExpect(jsonPath("$.id").exists());

        assertThat(body(result)).doesNotContain(PASSWORD).doesNotContain("bcrypt");
        String storedHash = jdbc.queryForObject(
                "select password_hash from app_users where username = 'asha.patel'", String.class);
        assertThat(storedHash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void usernamesAreUniqueRegardlessOfCase() throws Exception {
        register("asha", PASSWORD).andExpect(status().isCreated());

        register("ASHA", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username asha is already registered"));
    }

    @Test
    void weakOrMalformedRegistrationIsRejected() throws Exception {
        register("asha", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must be 12 to 72 characters"));
        register("asha patel!", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("username may contain only letters, digits, '.', '_' and '-'"));
    }

    @Test
    void passwordLongerThanBcryptCanHashIsRejectedNotTruncated() throws Exception {
        // 40 characters - within the character limit - but 80 bytes in UTF-8.
        register("asha", "é".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must be at most 72 bytes when encoded as UTF-8"));
    }

    // --- sign-in and tokens ------------------------------------------------

    @Test
    void issuedTokenGrantsAccess() throws Exception {
        register("asha", PASSWORD).andExpect(status().isCreated());

        ResultActions result = token("ASHA", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        String accessToken = JsonPath.read(body(result), "$.accessToken");
        mvc.perform(get("/api/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
        register("asha", PASSWORD).andExpect(status().isCreated());

        token("asha", "not-the-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        token("nobody", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        register("asha", PASSWORD).andExpect(status().isCreated());
        String accessToken = JsonPath.read(body(token("asha", PASSWORD)), "$.accessToken");

        // Change one character in the middle of the payload: the signature no longer matches.
        String[] parts = accessToken.split("\\.");
        int middle = parts[1].length() / 2;
        char replacement = parts[1].charAt(middle) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1].substring(0, middle) + replacement
                + parts[1].substring(middle + 1) + "." + parts[2];

        withBearer(tampered)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant twoHoursAgo = clock.instant().minus(Duration.ofHours(2));
        String expired = sign(JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER).subject("1")
                .issuedAt(twoHoursAgo).expiresAt(twoHoursAgo.plus(Duration.ofHours(1)))
                .build());

        withBearer(expired)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired access token"));
    }

    @Test
    void tokenFromAnotherIssuerIsRejectedEvenWithAValidSignature() throws Exception {
        Instant now = clock.instant();
        String foreign = sign(JwtClaimsSet.builder()
                .issuer("someone-else").subject("1")
                .issuedAt(now).expiresAt(now.plus(Duration.ofHours(1)))
                .build());

        withBearer(foreign).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageInTheAuthorizationHeaderIsRejected() throws Exception {
        withBearer("not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // --- helpers -----------------------------------------------------------

    private ResultActions register(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(username, password)));
    }

    private ResultActions token(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(username, password)));
    }

    private ResultActions withBearer(String token) throws Exception {
        return mvc.perform(get("/api/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String sign(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
