package com.ApnaAspatal.portal.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
 * A stored result must never silently look current after the inputs it was
 * computed from have changed - and the audit trail must show how the session
 * got to where it is. Runs against the real database; every test is rolled back.
 */
@SpringBootTest
@Transactional
class TriageResultApiIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mvc;
    private Map<String, Long> questionIds;
    private long userId;
    private long sessionId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        questionIds = TestData.resetQuestionBank(jdbc);
        userId = TestData.insertUser(jdbc, "result-test-user");
        sessionId = TestData.insertSession(jdbc, TestData.insertPatient(jdbc, userId, LocalDate.of(1980, 1, 15)));
    }

    // --- current vs stale --------------------------------------------------

    @Test
    void resultBeforeAnyEvaluationIsNotFound() throws Exception {
        perform(get("/api/triage-sessions/{id}/result", sessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Triage session " + sessionId + " has not been evaluated"));
    }

    @Test
    void freshResultIsCurrent() throws Exception {
        answerAll("CHEST_PAIN", "NO", "FEVER", "NO", "DURATION", "1 day");
        perform(post("/api/triage-sessions/{id}/evaluation", sessionId)).andExpect(status().isOk());

        result()
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.current").value(true));
    }

    @Test
    void changingAnAnswerMakesTheResultStaleWithoutChangingIt() throws Exception {
        answerAll("CHEST_PAIN", "YES", "FEVER", "NO", "DURATION", "2 days", "BREATHING_DIFFICULTY", "YES");
        Integer resultId = JsonPath.read(body(evaluate()), "$.id");

        answer("CHEST_PAIN", "NO").andExpect(status().isOk());

        // Still the HIGH result that was computed - but marked as no longer current.
        result()
                .andExpect(jsonPath("$.id").value(resultId))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.current").value(false));
    }

    @Test
    void reEvaluatingMakesTheResultCurrentAgainWithoutADuplicate() throws Exception {
        answerAll("CHEST_PAIN", "YES", "FEVER", "NO", "DURATION", "2 days", "BREATHING_DIFFICULTY", "YES");
        Integer resultId = JsonPath.read(body(evaluate()), "$.id");
        answer("CHEST_PAIN", "NO").andExpect(status().isOk());

        evaluate()
                .andExpect(jsonPath("$.id").value(resultId))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.current").value(true));
        result().andExpect(jsonPath("$.current").value(true));

        assertThat(jdbc.queryForObject(
                "select count(*) from triage_results where triage_session_id = ?", Integer.class, sessionId))
                .isEqualTo(1);
    }

    @Test
    void addingASymptomMakesTheResultStale() throws Exception {
        answerAll("CHEST_PAIN", "NO", "FEVER", "NO", "DURATION", "1 day");
        evaluate().andExpect(jsonPath("$.current").value(true));

        perform(post("/api/triage-sessions/{id}/symptoms", sessionId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Chest pain\",\"severity\":\"SEVERE\",\"onset\":\"SUDDEN\"}"))
                .andExpect(status().isCreated());

        result().andExpect(jsonPath("$.current").value(false));
    }

    @Test
    void resubmittingTheSameAnswerKeepsTheResultCurrent() throws Exception {
        answerAll("CHEST_PAIN", "NO", "FEVER", "NO", "DURATION", "1 day");
        evaluate().andExpect(jsonPath("$.current").value(true));

        answer("CHEST_PAIN", "no").andExpect(status().isOk());

        result().andExpect(jsonPath("$.current").value(true));
    }

    // --- audit trail -------------------------------------------------------

    @Test
    void auditTrailRecordsEveryAnswerChangeRemovalAndEvaluation() throws Exception {
        answerAll("CHEST_PAIN", "YES", "FEVER", "NO", "DURATION", "2 days", "BREATHING_DIFFICULTY", "YES");
        evaluate().andExpect(status().isOk());
        answer("CHEST_PAIN", "NO").andExpect(status().isOk());

        List<Map<String, Object>> events = jdbc.queryForList("""
                select event_type, subject, previous_value, new_value, inputs_version, actor_user_id
                from triage_audit_events where triage_session_id = ? order by id
                """, sessionId);

        assertThat(events).extracting(event -> event.get("event_type") + " " + event.get("subject"))
                .containsExactly(
                        "ANSWER_RECORDED CHEST_PAIN",
                        "ANSWER_RECORDED FEVER",
                        "ANSWER_RECORDED DURATION",
                        "ANSWER_RECORDED BREATHING_DIFFICULTY",
                        "EVALUATED HIGH",
                        "ANSWER_CHANGED CHEST_PAIN",
                        "ANSWER_REMOVED BREATHING_DIFFICULTY");

        Map<String, Object> changed = events.get(5);
        assertThat(changed.get("previous_value")).isEqualTo("YES");
        assertThat(changed.get("new_value")).isEqualTo("NO");
        // The removed answer's value survives in the audit log after the row is gone.
        assertThat(events.get(6).get("previous_value")).isEqualTo("YES");
        assertThat(events.get(4).get("new_value"))
                .isEqualTo("riskLevel=HIGH; department=EMERGENCY; reasonCodes=CHEST_PAIN,BREATHING_DIFFICULTY");
        assertThat(events).allSatisfy(event -> assertThat(event.get("actor_user_id")).isEqualTo(userId));
        // Four answers took the version to 4; the evaluation was at 4; the change took it to 5.
        assertThat(events).extracting(event -> event.get("inputs_version"))
                .containsExactly(1L, 2L, 3L, 4L, 4L, 5L, 5L);
    }

    // --- helpers -----------------------------------------------------------

    private ResultActions answer(String questionKey, String answer) throws Exception {
        return perform(post("/api/triage-sessions/{id}/answers", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"questionId\":" + questionIds.get(questionKey) + ",\"answer\":\"" + answer + "\"}"));
    }

    /** Answers in sequence - key, value, key, value - each a first answer expected to be created. */
    private void answerAll(String... keysAndValues) throws Exception {
        for (int i = 0; i < keysAndValues.length; i += 2) {
            answer(keysAndValues[i], keysAndValues[i + 1]).andExpect(status().isCreated());
        }
    }

    private ResultActions evaluate() throws Exception {
        return perform(post("/api/triage-sessions/{id}/evaluation", sessionId)).andExpect(status().isOk());
    }

    private ResultActions result() throws Exception {
        return perform(get("/api/triage-sessions/{id}/result", sessionId)).andExpect(status().isOk());
    }

    /**
     * Performs a request as the session owner, then flushes and clears the
     * persistence context so the next request reads from the database, as a
     * separate real request would.
     */
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        ResultActions result = mvc.perform(request.with(jwt().jwt(token -> token.subject(String.valueOf(userId)))));
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
