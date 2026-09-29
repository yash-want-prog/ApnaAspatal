package com.ApnaAspatal.portal.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ApnaAspatal.portal.support.TestData;
import com.jayway.jsonpath.JsonPath;

/**
 * Malformed input anywhere in the API produces a client error in the project's
 * standard {@link ApiError} shape - never a 500, and never Spring's default error
 * body. Runs against the real database; every test is rolled back.
 *
 * <p>Every request is authenticated, so these tests exercise input handling
 * behind the security layer. Unauthenticated requests are covered by
 * {@code AuthApiIntegrationTest}.
 */
@SpringBootTest
@Transactional
class ApiErrorHandlingIntegrationTest {

    private static final String MALFORMED = "Request body is missing or malformed";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;
    private long userId;

    @BeforeEach
    void setUp() {
        userId = TestData.insertUser(jdbc, "error-test-user");
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(jwt().jwt(token -> token.subject(String.valueOf(userId)))))
                .build();
    }

    @Test
    void malformedJsonIsABadRequestInTheStandardShape() throws Exception {
        ResultActions result = mvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(message(result)).isEqualTo(MALFORMED);
    }

    @Test
    void missingBodyIsABadRequest() throws Exception {
        ResultActions result = mvc.perform(post("/api/triage-sessions").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        assertThat(message(result)).isEqualTo(MALFORMED);
    }

    @Test
    void nonNumericIdIsABadRequestNamingTheParameter() throws Exception {
        ResultActions patient = mvc.perform(get("/api/patients/abc")).andExpect(status().isBadRequest());
        ResultActions session = mvc.perform(get("/api/triage-sessions/abc/next-question"))
                .andExpect(status().isBadRequest());

        assertThat(message(patient)).isEqualTo("Invalid value for 'id'");
        assertThat(message(session)).isEqualTo("Invalid value for 'sessionId'");
    }

    @Test
    void missingRequiredFieldIsABadRequestNamingTheField() throws Exception {
        ResultActions result = mvc.perform(post("/api/triage-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(message(result)).isEqualTo("patientId is required");
    }

    @Test
    void unknownEnumValueIsABadRequestAndNothingIsStored() throws Exception {
        long sessionId = TestData.insertSession(jdbc,
                TestData.insertPatient(jdbc, userId, LocalDate.of(1980, 1, 15)));

        ResultActions result = mvc.perform(post("/api/triage-sessions/{sessionId}/symptoms", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cough\",\"severity\":\"EXTREME\",\"onset\":\"SUDDEN\"}"))
                .andExpect(status().isBadRequest());

        assertThat(message(result)).isEqualTo(MALFORMED);
        assertThat(jdbc.queryForObject(
                "select count(*) from symptoms where triage_session_id = ?", Integer.class, sessionId)).isZero();
    }

    @Test
    void everyInvalidFieldIsReportedInADeterministicOrder() throws Exception {
        String overlongPhone = "9".repeat(256);

        ResultActions result = mvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \",\"email\":\"asha@example.com\",\"phone\":\""
                                + overlongPhone + "\",\"dateOfBirth\":\"1990-01-01\"}"))
                .andExpect(status().isBadRequest());

        assertThat(message(result)).isEqualTo("fullName is required; phone must be at most 255 characters");
    }

    @Test
    void futureDateOfBirthIsABadRequest() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();

        ResultActions result = mvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Asha Patel\",\"email\":\"asha@example.com\","
                                + "\"phone\":\"+919876543210\",\"dateOfBirth\":\"" + tomorrow + "\"}"))
                .andExpect(status().isBadRequest());

        assertThat(message(result)).isEqualTo("dateOfBirth must not be in the future");
    }

    @Test
    void startingASessionForAnUnknownPatientIsNotFound() throws Exception {
        mvc.perform(post("/api/triage-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":999999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unknownUrlIsNotFoundInTheStandardShape() throws Exception {
        mvc.perform(get("/api/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Not Found"));
    }

    @Test
    void unsupportedMethodIsMethodNotAllowedWithAnAllowHeader() throws Exception {
        mvc.perform(delete("/api/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(header().string("Allow", "GET"));
    }

    @Test
    void unsupportedMediaTypeIsRejected() throws Exception {
        mvc.perform(post("/api/patients").contentType(MediaType.TEXT_PLAIN).content("fullName=Asha"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    private static String message(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.message");
    }
}
