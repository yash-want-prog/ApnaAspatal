package com.ApnaAspatal.portal.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.Column;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ApnaAspatal.portal.patient.Patient;
import com.ApnaAspatal.portal.triage.engine.TriageDecision;
import com.ApnaAspatal.portal.triage.engine.TriageFacts;
import com.ApnaAspatal.portal.triage.engine.TriageFacts.SymptomFact;
import com.ApnaAspatal.portal.triage.engine.TriageRuleEngine;
import com.ApnaAspatal.portal.triage.question.TriageQuestion;

/**
 * The service must translate faithfully between the persisted domain and the
 * engine, use one explicit moment for all time calculations, and never let the
 * database be the first thing to reject bad data.
 *
 * <p>Repositories are mocked and the engine is a recording fake: these tests are
 * about the service's own logic, not about Hibernate or PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class TriageEvaluationServiceTest {

    private static final Long SESSION_ID = 7L;
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    /** 12:00 in Kolkata on 29 September 2026. */
    private static final Clock NOON_29_SEPT_KOLKATA =
            Clock.fixed(Instant.parse("2026-09-29T06:30:00Z"), KOLKATA);

    /** Born the day after the evaluation date, 65 years earlier: still 64. */
    private static final LocalDate BORN_30_SEPT_1961 = LocalDate.of(1961, 9, 30);

    private static final TriageDecision HIGH_EMERGENCY = new TriageDecision(
            TriageRiskLevel.HIGH, TriageDepartment.EMERGENCY,
            List.of("CHEST_PAIN_YES", "BREATHING_DIFFICULTY_YES"));

    @Mock
    private TriageSessionService triageSessionService;

    @Mock
    private TriageAnswerRepository triageAnswerRepository;

    @Mock
    private SymptomRepository symptomRepository;

    @Mock
    private TriageResultRepository triageResultRepository;

    private final RecordingEngine engine = new RecordingEngine();

    // --- successful evaluation ---------------------------------------------

    @Test
    void storesANewResultBuiltFromTheDecision() {
        TriageSession session = givenSessionFor(BORN_30_SEPT_1961);
        givenNoAnswersOrSymptoms();
        givenNoExistingResult();
        givenSaveReturnsItsArgument();
        engine.willReturn(HIGH_EMERGENCY);

        TriageResult result = serviceWith(NOON_29_SEPT_KOLKATA).evaluate(SESSION_ID);

        assertThat(result.getTriageSession()).isSameAs(session);
        assertThat(result.getRiskLevel()).isEqualTo(TriageRiskLevel.HIGH);
        assertThat(result.getRecommendedDepartment()).isEqualTo("EMERGENCY");
        assertThat(result.getReasonCodes()).isEqualTo("CHEST_PAIN_YES,BREATHING_DIFFICULTY_YES");
        assertThat(result.getEvaluatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 29, 12, 0));
        verify(triageResultRepository, times(1)).save(any());
    }

    // --- engine input ------------------------------------------------------

    @Test
    void handsTheEngineFactsBuiltFromAnswersSymptomsAndAge() {
        TriageSession session = givenSessionFor(BORN_30_SEPT_1961);
        LocalDateTime answeredAt = LocalDateTime.of(2026, 9, 29, 11, 0);
        when(triageAnswerRepository.findBySessionIdWithQuestion(SESSION_ID)).thenReturn(List.of(
                new TriageAnswer(session, question("CHEST_PAIN"), "YES", answeredAt),
                new TriageAnswer(session, question("FEVER"), "NO", answeredAt)));
        when(symptomRepository.findByTriageSessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of(
                new Symptom(session, "Chest pain", SymptomSeverity.SEVERE, SymptomOnset.SUDDEN, "2 hours"),
                new Symptom(session, "Nausea", SymptomSeverity.LOW, SymptomOnset.GRADUAL, null)));
        givenNoExistingResult();
        givenSaveReturnsItsArgument();
        engine.willReturn(HIGH_EMERGENCY);

        serviceWith(NOON_29_SEPT_KOLKATA).evaluate(SESSION_ID);

        TriageFacts facts = engine.receivedFacts;
        assertThat(facts.answersByQuestionKey()).isEqualTo(Map.of("CHEST_PAIN", "YES", "FEVER", "NO"));
        assertThat(facts.symptoms()).containsExactly(
                new SymptomFact("Chest pain", SymptomSeverity.SEVERE, SymptomOnset.SUDDEN, "2 hours"),
                new SymptomFact("Nausea", SymptomSeverity.LOW, SymptomOnset.GRADUAL, null));
        assertThat(facts.patientAge()).isEqualTo(Period.of(64, 11, 30));
        assertThat(engine.invocations).isEqualTo(1);
    }

    // --- time --------------------------------------------------------------

    @Test
    void calculatesAgeFromTheInjectedClockNotTheSystemClock() {
        givenSessionFor(BORN_30_SEPT_1961);
        givenNoAnswersOrSymptoms();
        givenNoExistingResult();
        givenSaveReturnsItsArgument();
        engine.willReturn(HIGH_EMERGENCY);

        serviceWith(NOON_29_SEPT_KOLKATA).evaluate(SESSION_ID);

        // One day before the 65th birthday, whenever this test runs.
        assertThat(engine.receivedFacts.ageInYears()).isEqualTo(64);
        assertThat(engine.receivedFacts.patientAge()).isEqualTo(Period.of(64, 11, 30));
    }

    @Test
    void ageAndEvaluatedAtComeFromTheSameMomentInTheConfiguredZone() {
        givenSessionFor(BORN_30_SEPT_1961);
        givenNoAnswersOrSymptoms();
        givenNoExistingResult();
        givenSaveReturnsItsArgument();
        engine.willReturn(HIGH_EMERGENCY);

        // 19:00 UTC on 29 Sept is 00:30 on 30 Sept in Kolkata - the birthday.
        Instant justAfterMidnightInKolkata = Instant.parse("2026-09-29T19:00:00Z");

        TriageResult inKolkata = serviceWith(Clock.fixed(justAfterMidnightInKolkata, KOLKATA))
                .evaluate(SESSION_ID);
        Period ageInKolkata = engine.receivedFacts.patientAge();

        serviceWith(Clock.fixed(justAfterMidnightInKolkata, ZoneOffset.UTC)).evaluate(SESSION_ID);
        Period ageInUtc = engine.receivedFacts.patientAge();

        // The same instant is a different date in each zone - which is why the
        // zone must be explicit.
        assertThat(ageInKolkata).isEqualTo(Period.ofYears(65));
        assertThat(ageInUtc).isEqualTo(Period.of(64, 11, 30));

        // And within one evaluation, age and timestamp agree on the date.
        assertThat(inKolkata.getEvaluatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 0, 30));
    }

    // --- missing or invalid age --------------------------------------------

    @Test
    void rejectsPatientWithoutDateOfBirth() {
        givenSessionFor(null);

        assertThatThrownBy(() -> serviceWith(NOON_29_SEPT_KOLKATA).evaluate(SESSION_ID))
                .isInstanceOf(PatientAgeUnavailableException.class)
                .hasMessageContaining("no date of birth");

        // Fails before gathering anything else, and nothing is decided or stored.
        assertThat(engine.invocations).isZero();
        verifyNoInteractions(triageAnswerRepository, symptomRepository, triageResultRepository);
    }

    @Test
    void rejectsDateOfBirthAfterTheEvaluationDate() {
        givenSessionFor(LocalDate.of(2026, 9, 30));

        assertThatThrownBy(() -> serviceWith(NOON_29_SEPT_KOLKATA).evaluate(SESSION_ID))
                .isInstanceOf(PatientAgeUnavailableException.class)
                .hasMessageContaining("after the evaluation date");

        assertThat(engine.invocations).isZero();
        verifyNoInteractions(triageResultRepository);
    }

    // --- repeated evaluation -----------------------------------------------

    @Test
    void updatesTheExistingResultInsteadOfInsertingASecondRow() {
        TriageSession session = givenSessionFor(BORN_30_SEPT_1961);
        givenNoAnswersOrSymptoms();
        TriageResult existing = new TriageResult(session, TriageRiskLevel.LOW, "GENERAL_MEDICINE",
                "NO_RED_FLAGS", LocalDateTime.of(2026, 9, 28, 9, 0));
        when(triageResultRepository.findByTriageSessionId(SESSION_ID)).thenReturn(Optional.of(existing));
        givenSaveReturnsItsArgument();
        engine.willReturn(HIGH_EMERGENCY);

        TriageResult result = serviceWith(NOON_29_SEPT_KOLKATA).evaluate(SESSION_ID);

        assertThat(result).isSameAs(existing);
        assertThat(existing.getRiskLevel()).isEqualTo(TriageRiskLevel.HIGH);
        assertThat(existing.getRecommendedDepartment()).isEqualTo("EMERGENCY");
        assertThat(existing.getReasonCodes()).isEqualTo("CHEST_PAIN_YES,BREATHING_DIFFICULTY_YES");
        assertThat(existing.getEvaluatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 29, 12, 0));
        assertThat(existing.getTriageSession()).isSameAs(session);
        verify(triageResultRepository, times(1)).save(existing);
    }

    // --- reason-code storage limit -----------------------------------------

    @Test
    void rejectsReasonCodesThatWouldNotFitTheColumn() {
        givenSessionFor(BORN_30_SEPT_1961);
        givenNoAnswersOrSymptoms();
        // 250 + 1 separator + 250 = 501 characters once joined.
        engine.willReturn(decisionWithCodes("A".repeat(250), "B".repeat(250)));

        assertThatThrownBy(() -> serviceWith(NOON_29_SEPT_KOLKATA).evaluate(SESSION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("501");

        verify(triageResultRepository, never()).save(any());
    }

    @Test
    void acceptsReasonCodesExactlyAtTheColumnLimit() {
        givenSessionFor(BORN_30_SEPT_1961);
        givenNoAnswersOrSymptoms();
        givenNoExistingResult();
        givenSaveReturnsItsArgument();
        // 250 + 1 separator + 249 = 500 characters once joined.
        engine.willReturn(decisionWithCodes("A".repeat(250), "B".repeat(249)));

        TriageResult result = serviceWith(NOON_29_SEPT_KOLKATA).evaluate(SESSION_ID);

        assertThat(result.getReasonCodes()).hasSize(500);
    }

    @Test
    void storageLimitMatchesTheReasonCodesColumn() throws NoSuchFieldException {
        Column column = TriageResult.class.getDeclaredField("reasonCodes").getAnnotation(Column.class);

        assertThat(column.length()).isEqualTo(TriageEvaluationService.MAX_STORED_REASON_CODES_LENGTH);
    }

    // --- engine contract and missing session -------------------------------

    @Test
    void rejectsANullDecisionFromTheEngine() {
        givenSessionFor(BORN_30_SEPT_1961);
        givenNoAnswersOrSymptoms();
        engine.willReturn(null);

        assertThatThrownBy(() -> serviceWith(NOON_29_SEPT_KOLKATA).evaluate(SESSION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no decision");

        verify(triageResultRepository, never()).save(any());
    }

    @Test
    void propagatesSessionNotFound() {
        when(triageSessionService.getSessionById(SESSION_ID))
                .thenThrow(new TriageSessionNotFoundException(SESSION_ID));

        assertThatThrownBy(() -> serviceWith(NOON_29_SEPT_KOLKATA).evaluate(SESSION_ID))
                .isInstanceOf(TriageSessionNotFoundException.class);

        assertThat(engine.invocations).isZero();
    }

    // --- helpers -----------------------------------------------------------

    private TriageEvaluationService serviceWith(Clock clock) {
        return new TriageEvaluationService(triageSessionService, triageAnswerRepository,
                symptomRepository, triageResultRepository, engine, clock);
    }

    private TriageSession givenSessionFor(LocalDate dateOfBirth) {
        Patient patient = new Patient("Asha Patel", "asha@example.com", "+919876543210", dateOfBirth);
        TriageSession session = new TriageSession(patient, TriageSessionStatus.IN_PROGRESS,
                LocalDateTime.of(2026, 9, 29, 10, 0));
        when(triageSessionService.getSessionById(SESSION_ID)).thenReturn(session);
        return session;
    }

    private void givenNoAnswersOrSymptoms() {
        when(triageAnswerRepository.findBySessionIdWithQuestion(SESSION_ID)).thenReturn(List.of());
        when(symptomRepository.findByTriageSessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of());
    }

    private void givenNoExistingResult() {
        when(triageResultRepository.findByTriageSessionId(SESSION_ID)).thenReturn(Optional.empty());
    }

    private void givenSaveReturnsItsArgument() {
        when(triageResultRepository.save(any(TriageResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static TriageQuestion question(String key) {
        return new TriageQuestion(key, key + "?", "BOOLEAN");
    }

    private static TriageDecision decisionWithCodes(String... codes) {
        return new TriageDecision(TriageRiskLevel.MODERATE, TriageDepartment.GENERAL_MEDICINE, List.of(codes));
    }

    /**
     * A fake engine that records what it was given. Simpler to read than a
     * Mockito mock with an argument captor, and it keeps the engine's input
     * inspectable after the call.
     */
    private static final class RecordingEngine implements TriageRuleEngine {

        private TriageDecision decisionToReturn;
        private TriageFacts receivedFacts;
        private int invocations;

        void willReturn(TriageDecision decision) {
            this.decisionToReturn = decision;
        }

        @Override
        public TriageDecision evaluate(TriageFacts facts) {
            invocations++;
            receivedFacts = facts;
            return decisionToReturn;
        }
    }
}
