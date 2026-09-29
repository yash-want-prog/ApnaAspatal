package com.ApnaAspatal.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ApnaAspatal.portal.support.TestData;
import com.jayway.jsonpath.JsonPath;

/**
 * One account can never reach another account's patients or triage sessions -
 * not by guessing ids, and not through any endpoint. Such records are reported
 * exactly like records that do not exist.
 *
 * <p>Alice owns a patient and a session; Bob tries to use them. Runs against the
 * real database; every test is rolled back.
 */
@SpringBootTest
@Transactional
class AccessControlIntegrationTest {

    private static final String PATIENT_JSON = "{\"fullName\":\"%s\",\"email\":\"p@example.com\","
            + "\"phone\":\"+919876543210\",\"dateOfBirth\":\"1990-01-01\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;
    private Map<String, Long> questionIds;
    private long alice;
    private long bob;
    private long alicesPatient;
    private long alicesSession;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        questionIds = TestData.resetQuestionBank(jdbc);
        alice = TestData.insertUser(jdbc, "alice-access-test");
        bob = TestData.insertUser(jdbc, "bob-access-test");
        alicesPatient = TestData.insertPatient(jdbc, alice, LocalDate.of(1970, 3, 1));
        alicesSession = TestData.insertSession(jdbc, alicesPatient);
    }

    // --- patients ----------------------------------------------------------

    @Test
    void patientListShowsOnlyYourOwnPatients() throws Exception {
        as(bob, post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                .content(PATIENT_JSON.formatted("Bob's Patient")))
                .andExpect(status().isCreated());

        List<String> bobsNames = JsonPath.read(body(as(bob, get("/api/patients"))), "$[*].fullName");
        List<Integer> alicesIds = JsonPath.read(body(as(alice, get("/api/patients"))), "$[*].id");

        assertThat(bobsNames).containsExactly("Bob's Patient");
        assertThat(alicesIds).containsExactly((int) alicesPatient);
    }

    @Test
    void anotherAccountsPatientCannotBeReadChangedOrDeleted() throws Exception {
        as(bob, get("/api/patients/{id}", alicesPatient)).andExpect(status().isNotFound());
        as(bob, put("/api/patients/{id}", alicesPatient).contentType(MediaType.APPLICATION_JSON)
                .content(PATIENT_JSON.formatted("Renamed By Bob")))
                .andExpect(status().isNotFound());
        as(bob, delete("/api/patients/{id}", alicesPatient)).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select full_name from patients where id = ?", String.class, alicesPatient))
                .isEqualTo("Integration Test Patient");
        as(alice, get("/api/patients/{id}", alicesPatient)).andExpect(status().isOk());
    }

    @Test
    void anotherAccountsPatientLooksExactlyLikeOneThatDoesNotExist() throws Exception {
        String notYours = message(as(bob, get("/api/patients/{id}", alicesPatient)));
        String nonexistent = message(as(bob, get("/api/patients/{id}", 999_999_999L)));

        // Same status, same wording: Bob cannot tell a real id from a made-up one.
        assertThat(notYours).isEqualTo("Patient not found with id: " + alicesPatient);
        assertThat(nonexistent).isEqualTo("Patient not found with id: 999999999");
    }

    @Test
    void cannotStartASessionForAnotherAccountsPatient() throws Exception {
        as(bob, post("/api/triage-sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":" + alicesPatient + "}"))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject(
                "select count(*) from triage_sessions where patient_id = ?", Integer.class, alicesPatient))
                .isEqualTo(1);
    }

    // --- triage sessions ---------------------------------------------------

    @Test
    void anotherAccountsSessionIsNotFoundOnEveryEndpoint() throws Exception {
        as(bob, get("/api/triage-sessions/{id}/next-question", alicesSession)).andExpect(status().isNotFound());
        as(bob, post("/api/triage-sessions/{id}/answers", alicesSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionId\":" + questionIds.get("CHEST_PAIN") + ",\"answer\":\"YES\"}"))
                .andExpect(status().isNotFound());
        as(bob, post("/api/triage-sessions/{id}/symptoms", alicesSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cough\",\"severity\":\"LOW\",\"onset\":\"GRADUAL\"}"))
                .andExpect(status().isNotFound());
        as(bob, post("/api/triage-sessions/{id}/evaluation", alicesSession)).andExpect(status().isNotFound());
        as(bob, get("/api/triage-sessions/{id}/result", alicesSession)).andExpect(status().isNotFound());

        // Nothing was written to Alice's session.
        for (String table : List.of("triage_answers", "symptoms", "triage_results", "triage_audit_events")) {
            assertThat(jdbc.queryForObject(
                    "select count(*) from " + table + " where triage_session_id = ?", Integer.class, alicesSession))
                    .as(table).isZero();
        }
    }

    @Test
    void ownerCanUseTheirOwnSession() throws Exception {
        as(alice, get("/api/triage-sessions/{id}/next-question", alicesSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionKey").value("CHEST_PAIN"));
        as(alice, post("/api/triage-sessions/{id}/answers", alicesSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionId\":" + questionIds.get("CHEST_PAIN") + ",\"answer\":\"NO\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void recordsCreatedBeforeAccountsExistedAreReachableByNoOne() throws Exception {
        long ownerless = TestData.insertPatient(jdbc, null, LocalDate.of(1960, 1, 1));

        as(alice, get("/api/patients/{id}", ownerless)).andExpect(status().isNotFound());
        as(bob, get("/api/patients/{id}", ownerless)).andExpect(status().isNotFound());
    }

    // --- helpers -----------------------------------------------------------

    private ResultActions as(long userId, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(jwt().jwt(token -> token.subject(String.valueOf(userId)))));
    }

    private static String message(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.message");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
