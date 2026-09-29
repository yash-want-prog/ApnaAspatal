package com.ApnaAspatal.portal.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ApnaAspatal.portal.support.TestData;
import com.ApnaAspatal.portal.triage.dto.TriageAnswerRequest;
import com.ApnaAspatal.portal.triage.question.QuestionAnswerType;
import com.ApnaAspatal.portal.triage.question.TriageQuestion;

/**
 * Concurrent changes to one session run one at a time.
 *
 * <p>A row lock only matters between separate transactions, so - unlike the
 * other integration tests - this one commits real data. It creates its own
 * uniquely named account, patient, and session, and deletes everything it
 * created afterwards, whether the test passed or not.
 */
@SpringBootTest
class SessionLockingIntegrationTest {

    private static final long TIMEOUT_SECONDS = 20;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TriageSessionService triageSessionService;

    @Autowired
    private TriageAnswerService triageAnswerService;

    private final ExecutorService threads = Executors.newFixedThreadPool(2);

    private long userId;
    private long patientId;
    private long sessionId;

    @BeforeEach
    void createCommittedSession() {
        userId = TestData.insertUser(jdbc, "lock-test-" + UUID.randomUUID().toString().substring(0, 8));
        patientId = TestData.insertPatient(jdbc, userId, LocalDate.of(1975, 6, 1));
        sessionId = TestData.insertSession(jdbc, patientId);
    }

    @AfterEach
    void deleteEverythingThisTestCreated() {
        threads.shutdownNow();
        for (String table : List.of("triage_audit_events", "triage_answers", "symptoms", "triage_results")) {
            jdbc.update("delete from " + table + " where triage_session_id = ?", sessionId);
        }
        jdbc.update("delete from triage_sessions where id = ?", sessionId);
        jdbc.update("delete from patients where id = ?", patientId);
        jdbc.update("delete from app_users where id = ?", userId);
    }

    @Test
    void submissionWaitsForTheLockBeforeReadingSoItSeesTheOtherTransactionsChange() throws Exception {
        // Waiting alone proves little: even without the lock, a submission would
        // eventually block when it writes to the session row. What matters is that
        // it waits BEFORE reading the session's answers, so its decisions are made
        // on up-to-date state. The holder therefore records an answer while it
        // holds the lock; the waiting submission must see it and replace it.
        TriageAnswerRequest answer = validAnswerToTheCurrentQuestion();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> holder = threads.submit(() -> asUser(() -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                triageSessionService.getSessionForUpdate(sessionId);
                jdbc.update("""
                        insert into triage_answers (triage_session_id, triage_question_id, answer_value, answered_at)
                        values (?, ?, ?, now())
                        """, sessionId, answer.questionId(), answer.answer());
                locked.countDown();
                awaitQuietly(release);
            });
            return null;
        }));
        assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("lock acquired").isTrue();

        Future<TriageAnswerService.SubmittedAnswer> submission =
                threads.submit(() -> asUser(() -> triageAnswerService.submitAnswer(sessionId, answer)));
        assertThatThrownBy(() -> submission.get(1, TimeUnit.SECONDS))
                .as("submission waits while the lock is held")
                .isInstanceOf(TimeoutException.class);

        release.countDown();
        holder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Had it read before waiting, it would have seen no answer and tried to insert
        // a duplicate. Instead it saw the holder's committed answer and replaced it.
        assertThat(submission.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).created()).isFalse();
        assertThat(storedAnswerCount()).isEqualTo(1);
    }

    @Test
    void simultaneousAnswersToTheSameQuestionAreAppliedOneAfterTheOther() throws Exception {
        TriageAnswerRequest answer = validAnswerToTheCurrentQuestion();
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<TriageAnswerService.SubmittedAnswer> submit = () -> asUser(() -> {
            start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return triageAnswerService.submitAnswer(sessionId, answer);
        });

        Future<TriageAnswerService.SubmittedAnswer> first = threads.submit(submit);
        Future<TriageAnswerService.SubmittedAnswer> second = threads.submit(submit);

        // Serialised: exactly one creates the answer and the other replaces it. Unserialised,
        // both could see "no answer yet" and both try to insert one.
        List<Boolean> created = List.of(
                first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).created(),
                second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).created());
        assertThat(created).containsExactlyInAnyOrder(true, false);
        assertThat(storedAnswerCount()).isEqualTo(1);
    }

    // --- helpers -----------------------------------------------------------

    /**
     * A valid answer to whatever question is currently due. Works with the
     * committed question bank as it is, instead of assuming its contents.
     */
    private TriageAnswerRequest validAnswerToTheCurrentQuestion() throws Exception {
        TriageQuestion due = asUser(() -> triageAnswerService.findNextQuestion(sessionId)).orElseThrow();
        String value = due.getAnswerType() == QuestionAnswerType.BOOLEAN ? "NO"
                : due.getAnswerType() == QuestionAnswerType.NUMBER ? "37.0"
                : "lock test";
        return new TriageAnswerRequest(due.getId(), value);
    }

    /** Runs as the test account on the current thread - security context is per thread. */
    private <T> T asUser(Callable<T> work) throws Exception {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "HS256").subject(String.valueOf(userId)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            return work.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private int storedAnswerCount() {
        return jdbc.queryForObject(
                "select count(*) from triage_answers where triage_session_id = ?", Integer.class, sessionId);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
