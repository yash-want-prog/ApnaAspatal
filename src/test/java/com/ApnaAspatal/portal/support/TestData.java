package com.ApnaAspatal.portal.support;

import java.sql.Types;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test data for integration tests that run against the real database.
 *
 * <p>Always call these inside a test-managed transaction ({@code @Transactional}
 * on the test class): every change is rolled back when the test ends, so the
 * database is left exactly as it was.
 */
public final class TestData {

    /**
     * The canonical question bank, in asking order, as defined by
     * {@code data.sql}. Each entry: key, text, answer type, depends-on key,
     * depends-on answer.
     */
    private static final List<String[]> CANONICAL_BANK = List.of(
            new String[] {"CHEST_PAIN", "Are you experiencing chest pain?", "BOOLEAN", null, null},
            new String[] {"FEVER", "Do you have a fever?", "BOOLEAN", null, null},
            new String[] {"DURATION", "How long have you had these symptoms?", "TEXT", null, null},
            new String[] {"BREATHING_DIFFICULTY", "Are you having difficulty breathing?", "BOOLEAN",
                    "CHEST_PAIN", "YES"},
            new String[] {"TEMPERATURE", "What is your temperature (°C)?", "NUMBER", "FEVER", "YES"});

    private TestData() {
    }

    /**
     * Makes the canonical questions exactly as defined and the only active ones,
     * whatever else the database holds.
     *
     * @return question ids by key, in asking order
     * @throws IllegalStateException if the ids are not in the canonical asking
     *                               order, which every flow test relies on
     */
    public static Map<String, Long> resetQuestionBank(JdbcTemplate jdbc) {
        jdbc.update("update triage_questions set active = false");

        Map<String, Long> ids = new LinkedHashMap<>();
        for (String[] question : CANONICAL_BANK) {
            jdbc.update("""
                    insert into triage_questions
                        (question_key, question_text, answer_type, active, depends_on_question_key, depends_on_answer)
                    values (?, ?, ?, true, ?, ?)
                    on conflict (question_key) do update set
                        question_text = excluded.question_text,
                        answer_type = excluded.answer_type,
                        active = true,
                        depends_on_question_key = excluded.depends_on_question_key,
                        depends_on_answer = excluded.depends_on_answer
                    """,
                    text(question[0]), text(question[1]), text(question[2]), text(question[3]), text(question[4]));
            ids.put(question[0], jdbc.queryForObject(
                    "select id from triage_questions where question_key = ?", Long.class, question[0]));
        }

        List<Long> inOrder = List.copyOf(ids.values());
        for (int i = 1; i < inOrder.size(); i++) {
            if (inOrder.get(i) <= inOrder.get(i - 1)) {
                throw new IllegalStateException("Question ids are not in canonical asking order: " + ids);
            }
        }
        return ids;
    }

    public static void deactivateQuestion(JdbcTemplate jdbc, String questionKey) {
        jdbc.update("update triage_questions set active = false where question_key = ?", questionKey);
    }

    /**
     * An account to own test records. The password hash is a placeholder: tests
     * that authenticate for real register through the API instead.
     */
    public static long insertUser(JdbcTemplate jdbc, String username) {
        return jdbc.queryForObject("""
                insert into app_users (username, password_hash, created_at)
                values (?, '{noop}not-a-real-password', now())
                returning id
                """, Long.class, username);
    }

    /**
     * @param ownerId     the owning account, or null to model a record created before accounts existed
     * @param dateOfBirth may be null, to model a legacy record with no date of birth
     */
    public static long insertPatient(JdbcTemplate jdbc, Long ownerId, LocalDate dateOfBirth) {
        return jdbc.queryForObject("""
                insert into patients (full_name, email, phone, date_of_birth, owner_id)
                values ('Integration Test Patient', 'integration-test@example.com', '+910000000000', ?, ?)
                returning id
                """, Long.class, new SqlParameterValue(Types.DATE, dateOfBirth),
                new SqlParameterValue(Types.BIGINT, ownerId));
    }

    public static long insertSession(JdbcTemplate jdbc, long patientId) {
        return jdbc.queryForObject("""
                insert into triage_sessions (patient_id, status, started_at)
                values (?, 'IN_PROGRESS', now())
                returning id
                """, Long.class, patientId);
    }

    private static SqlParameterValue text(String value) {
        return new SqlParameterValue(Types.VARCHAR, value);
    }
}
