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
 * The question flow and evaluation over HTTP, against the real database.
 *
 * <p>Each test runs in a transaction that is rolled back afterwards, so nothing
 * persists. After every request the persistence context is flushed and cleared,
 * so the next request - like a real one - reads from the database rather than
 * from entities cached by the previous one.
 *
 * <p>Requests are authenticated as the account that owns the session's patient,
 * through the real security filter chain. Access by other accounts is covered by
 * {@code AccessControlIntegrationTest}.
 */
@SpringBootTest
@Transactional
class TriageFlowApiIntegrationTest {

    private static final long NO_SUCH_ID = 999_999_999L;

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
        userId = TestData.insertUser(jdbc, "flow-test-user");
        sessionId = TestData.insertSession(jdbc, TestData.insertPatient(jdbc, userId, LocalDate.of(1980, 1, 15)));
    }

    // --- answer types ------------------------------------------------------

    @Test
    void validYesNoAnswerIsStoredInCanonicalForm() throws Exception {
        answer("CHEST_PAIN", "yes")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questionKey").value("CHEST_PAIN"))
                .andExpect(jsonPath("$.answer").value("YES"));

        assertThat(storedAnswer("CHEST_PAIN")).isEqualTo("YES");
    }

    @Test
    void invalidYesNoAnswerIsRejectedWithAnErrorBodyAndNotStored() throws Exception {
        ResultActions result = answer("CHEST_PAIN", "maybe")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(message(result)).isEqualTo("Answer to CHEST_PAIN must be YES or NO");
        assertThat(storedAnswerCount()).isZero();
    }

    @Test
    void invalidAnswerNeverOverwritesAnEarlierValidAnswer() throws Exception {
        answer("CHEST_PAIN", "YES").andExpect(status().isCreated());

        answer("CHEST_PAIN", "maybe").andExpect(status().isBadRequest());

        assertThat(storedAnswer("CHEST_PAIN")).isEqualTo("YES");
    }

    @Test
    void validNumberIsAcceptedOnceTheTemperatureQuestionIsDue() throws Exception {
        answerAll("CHEST_PAIN", "NO", "FEVER", "YES", "DURATION", "2 days");

        answer("TEMPERATURE", "38.5")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answer").value("38.5"));

        assertThat(storedAnswer("TEMPERATURE")).isEqualTo("38.5");
    }

    @Test
    void invalidNumberIsRejectedAndNotStored() throws Exception {
        answerAll("CHEST_PAIN", "NO", "FEVER", "YES", "DURATION", "2 days");

        ResultActions result = answer("TEMPERATURE", "38,5").andExpect(status().isBadRequest());

        assertThat(message(result)).contains("TEMPERATURE", "plain number");
        assertThat(storedAnswerFor("TEMPERATURE")).isEmpty();
    }

    @Test
    void validTextIsStoredExactlyAsGiven() throws Exception {
        answerAll("CHEST_PAIN", "NO", "FEVER", "NO");

        answer("DURATION", "about 2 days").andExpect(status().isCreated());

        assertThat(storedAnswer("DURATION")).isEqualTo("about 2 days");
    }

    @Test
    void blankTextIsRejectedAndNotStored() throws Exception {
        answerAll("CHEST_PAIN", "NO", "FEVER", "NO");

        ResultActions result = answer("DURATION", "   ").andExpect(status().isBadRequest());

        assertThat(message(result)).isEqualTo("answer is required");
        assertThat(storedAnswerFor("DURATION")).isEmpty();
    }

    // --- which question may be answered ------------------------------------

    @Test
    void answerToANonexistentQuestionIsNotFound() throws Exception {
        ResultActions result = perform(post("/api/triage-sessions/{sessionId}/answers", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody(NO_SUCH_ID, "YES")))
                .andExpect(status().isNotFound());

        assertThat(message(result)).contains("not found");
        assertThat(storedAnswerCount()).isZero();
    }

    @Test
    void answerToAnInactiveQuestionIsRejectedAndNotStored() throws Exception {
        TestData.deactivateQuestion(jdbc, "CHEST_PAIN");

        ResultActions result = answer("CHEST_PAIN", "YES").andExpect(status().isConflict());

        assertThat(message(result)).contains("no longer asked");
        assertThat(storedAnswerCount()).isZero();
    }

    @Test
    void questionThatIsNotYetDueCannotBeAnswered() throws Exception {
        ResultActions result = answer("FEVER", "NO").andExpect(status().isConflict());

        assertThat(message(result)).contains("current question is CHEST_PAIN");
        assertThat(storedAnswerCount()).isZero();
    }

    @Test
    void lockedFollowUpCannotBeAnswered() throws Exception {
        // FEVER has not been answered YES, so TEMPERATURE's branch is not open.
        ResultActions result = answer("TEMPERATURE", "38.5").andExpect(status().isConflict());

        assertThat(message(result)).contains("depends on the answer to FEVER");
        assertThat(storedAnswerCount()).isZero();
    }

    @Test
    void followUpCanBeAnsweredOnceItIsDue() throws Exception {
        answerAll("CHEST_PAIN", "YES", "FEVER", "NO", "DURATION", "1 day");

        answer("BREATHING_DIFFICULTY", "YES").andExpect(status().isCreated());

        assertThat(storedAnswer("BREATHING_DIFFICULTY")).isEqualTo("YES");
    }

    @Test
    void answeringAgainReplacesTheExistingAnswer() throws Exception {
        answer("CHEST_PAIN", "YES").andExpect(status().isCreated());

        // 200, not 201: the existing answer is replaced and nothing new is created.
        answer("CHEST_PAIN", "NO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("NO"));

        assertThat(storedAnswerFor("CHEST_PAIN")).containsExactly("NO");
    }

    // --- stale follow-up answers -------------------------------------------

    @Test
    void closingABranchRemovesItsAnswersAndKeepsUnrelatedOnes() throws Exception {
        answerAll("CHEST_PAIN", "YES", "FEVER", "NO", "DURATION", "2 days", "BREATHING_DIFFICULTY", "YES");

        answer("CHEST_PAIN", "NO").andExpect(status().isOk());

        assertThat(storedAnswerFor("BREATHING_DIFFICULTY")).isEmpty();
        assertThat(storedAnswer("FEVER")).isEqualTo("NO");
        assertThat(storedAnswer("DURATION")).isEqualTo("2 days");
        // Nothing left to ask: the only open branch was the one just closed.
        perform(get("/api/triage-sessions/{sessionId}/next-question", sessionId))
                .andExpect(status().isNoContent());
    }

    @Test
    void reopeningABranchAsksItsFollowUpAgain() throws Exception {
        answerAll("CHEST_PAIN", "YES", "FEVER", "NO", "DURATION", "2 days", "BREATHING_DIFFICULTY", "YES");
        answer("CHEST_PAIN", "NO").andExpect(status().isOk());

        answer("CHEST_PAIN", "YES").andExpect(status().isOk());

        perform(get("/api/triage-sessions/{sessionId}/next-question", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionKey").value("BREATHING_DIFFICULTY"));
    }

    // --- evaluation --------------------------------------------------------

    @Test
    void evaluationUsesTheCurrentPathNotAClosedBranch() throws Exception {
        answerAll("CHEST_PAIN", "YES", "FEVER", "NO", "DURATION", "2 days", "BREATHING_DIFFICULTY", "YES");

        ResultActions high = evaluate().andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.recommendedDepartment").value("EMERGENCY"))
                .andExpect(jsonPath("$.current").value(true));
        assertThat(reasonCodes(high)).containsExactly("CHEST_PAIN", "BREATHING_DIFFICULTY");

        // Changing CHEST_PAIN closes the breathing branch. Its YES must no longer
        // count, or the patient would still be triaged as HIGH.
        answer("CHEST_PAIN", "NO").andExpect(status().isOk());

        ResultActions low = evaluate().andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.current").value(true));
        assertThat(reasonCodes(low)).containsExactly("NO_HIGH_RISK_RULE_MATCHED");

        // Re-evaluation replaced the session's one result rather than adding another.
        assertThat(jdbc.queryForList(
                "select risk_level from triage_results where triage_session_id = ?", String.class, sessionId))
                .containsExactly("LOW");
    }

    @Test
    void evaluatingWithRequiredAnswersMissingIsInsufficientNotLow() throws Exception {
        ResultActions result = evaluate().andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("MODERATE"))
                .andExpect(jsonPath("$.recommendedDepartment").value("GENERAL_MEDICINE"));

        assertThat(reasonCodes(result)).containsExactly("INSUFFICIENT_INFORMATION");
    }

    @Test
    void evaluatingAPatientWithoutADateOfBirthIsRejected() throws Exception {
        sessionId = TestData.insertSession(jdbc, TestData.insertPatient(jdbc, userId, null));

        ResultActions result = evaluate().andExpect(status().isUnprocessableContent());

        assertThat(message(result)).contains("no date of birth");
        assertThat(jdbc.queryForObject(
                "select count(*) from triage_results where triage_session_id = ?", Integer.class, sessionId))
                .isZero();
    }

    @Test
    void unknownSessionIsNotFoundEverywhere() throws Exception {
        perform(get("/api/triage-sessions/{sessionId}/next-question", NO_SUCH_ID))
                .andExpect(status().isNotFound());
        perform(post("/api/triage-sessions/{sessionId}/answers", NO_SUCH_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody(questionIds.get("CHEST_PAIN"), "YES")))
                .andExpect(status().isNotFound());
        perform(post("/api/triage-sessions/{sessionId}/evaluation", NO_SUCH_ID))
                .andExpect(status().isNotFound());
    }

    // --- helpers -----------------------------------------------------------

    private ResultActions answer(String questionKey, String answer) throws Exception {
        return perform(post("/api/triage-sessions/{sessionId}/answers", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(answerBody(questionIds.get(questionKey), answer)));
    }

    /** Answers in sequence - key, value, key, value - each expected to succeed. */
    private void answerAll(String... keysAndValues) throws Exception {
        for (int i = 0; i < keysAndValues.length; i += 2) {
            answer(keysAndValues[i], keysAndValues[i + 1]).andExpect(status().isCreated());
        }
    }

    private ResultActions evaluate() throws Exception {
        return perform(post("/api/triage-sessions/{sessionId}/evaluation", sessionId));
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

    private String storedAnswer(String questionKey) {
        List<String> values = storedAnswerFor(questionKey);
        assertThat(values).as("stored answers to %s", questionKey).hasSize(1);
        return values.get(0);
    }

    private List<String> storedAnswerFor(String questionKey) {
        return jdbc.queryForList("""
                select a.answer_value from triage_answers a
                join triage_questions q on q.id = a.triage_question_id
                where a.triage_session_id = ? and q.question_key = ?
                """, String.class, sessionId, questionKey);
    }

    private int storedAnswerCount() {
        return jdbc.queryForObject(
                "select count(*) from triage_answers where triage_session_id = ?", Integer.class, sessionId);
    }

    private static String answerBody(Long questionId, String answer) {
        String escaped = answer.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"questionId\":" + questionId + ",\"answer\":\"" + escaped + "\"}";
    }

    private static String message(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.message");
    }

    private static List<String> reasonCodes(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.reasonCodes");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
