package com.ApnaAspatal.portal.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ApnaAspatal.portal.patient.Patient;
import com.ApnaAspatal.portal.triage.audit.TriageAuditLog;
import com.ApnaAspatal.portal.triage.dto.TriageAnswerRequest;
import com.ApnaAspatal.portal.triage.dto.TriageAnswerResponse;
import com.ApnaAspatal.portal.triage.question.QuestionAnswerType;
import com.ApnaAspatal.portal.triage.question.TriageQuestion;
import com.ApnaAspatal.portal.triage.question.TriageQuestionService;

/**
 * The question flow: which question comes next, which questions may be answered,
 * how answers are recorded, and how answers that fall off the path are removed.
 */
@ExtendWith(MockitoExtension.class)
class TriageAnswerServiceTest {

    private static final Long SESSION_ID = 3L;

    /** 12:00 in Kolkata on 29 September 2026. */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-29T06:30:00Z"), ZoneId.of("Asia/Kolkata"));

    @Mock
    private TriageAnswerRepository triageAnswerRepository;

    @Mock
    private TriageSessionService triageSessionService;

    @Mock
    private TriageQuestionService triageQuestionService;

    @Mock
    private TriageAuditLog triageAuditLog;

    private final TriageSession session = new TriageSession(
            new Patient("Asha Patel", "asha@example.com", "+919876543210", LocalDate.of(1991, 4, 12)),
            TriageSessionStatus.IN_PROGRESS,
            LocalDateTime.of(2026, 9, 29, 10, 0));

    // The seeded question bank, in id order.
    private final TriageQuestion chestPain = question(1L, "CHEST_PAIN", QuestionAnswerType.BOOLEAN, null, null);
    private final TriageQuestion fever = question(2L, "FEVER", QuestionAnswerType.BOOLEAN, null, null);
    private final TriageQuestion breathing =
            question(4L, "BREATHING_DIFFICULTY", QuestionAnswerType.BOOLEAN, "CHEST_PAIN", "YES");
    private final TriageQuestion temperature =
            question(5L, "TEMPERATURE", QuestionAnswerType.NUMBER, "FEVER", "YES");

    private final List<TriageQuestion> bank = List.of(chestPain, fever, breathing, temperature);

    // --- next question: adaptive selection ---------------------------------

    @Test
    void startsWithTheFirstRootQuestion() {
        givenActiveQuestions(chestPain, fever, breathing, temperature);
        givenAnswers();

        assertThat(service().findNextQuestion(SESSION_ID)).contains(chestPain);
    }

    @Test
    void followUpBecomesNextWhenItsPrerequisiteHasTheRequiredAnswer() {
        givenActiveQuestions(chestPain, fever, breathing, temperature);
        givenAnswers(answer(chestPain, "YES"));

        // FEVER (id 2) is still unanswered, so it comes before BREATHING (id 4).
        assertThat(service().findNextQuestion(SESSION_ID)).contains(fever);

        givenAnswers(answer(chestPain, "YES"), answer(fever, "NO"));
        assertThat(service().findNextQuestion(SESSION_ID)).contains(breathing);
    }

    @Test
    void prerequisiteAnswerIsMatchedRegardlessOfCase() {
        givenActiveQuestions(chestPain, breathing);
        givenAnswers(answer(chestPain, "yes"));

        assertThat(service().findNextQuestion(SESSION_ID)).contains(breathing);
    }

    @Test
    void followUpIsSkippedWhenItsPrerequisiteHasADifferentAnswer() {
        givenActiveQuestions(chestPain, fever, breathing, temperature);
        givenAnswers(answer(chestPain, "NO"), answer(fever, "YES"));

        assertThat(service().findNextQuestion(SESSION_ID)).contains(temperature);
    }

    @Test
    void followUpIsNotAskedBeforeItsPrerequisiteEvenIfListedFirst() {
        givenActiveQuestions(breathing, chestPain);
        givenAnswers();

        assertThat(service().findNextQuestion(SESSION_ID)).contains(chestPain);
    }

    @Test
    void nothingIsNextOnceEveryEligibleQuestionIsAnswered() {
        givenActiveQuestions(chestPain, fever, breathing, temperature);
        givenAnswers(answer(chestPain, "NO"), answer(fever, "NO"));

        // BREATHING and TEMPERATURE were never eligible; the flow is complete.
        assertThat(service().findNextQuestion(SESSION_ID)).isEmpty();
    }

    @Test
    void unknownSessionHasNoNextQuestion() {
        when(triageSessionService.getSessionById(SESSION_ID))
                .thenThrow(new TriageSessionNotFoundException(SESSION_ID));

        assertThatThrownBy(() -> service().findNextQuestion(SESSION_ID))
                .isInstanceOf(TriageSessionNotFoundException.class);
    }

    // --- which question may be answered ------------------------------------

    @Test
    void currentQuestionCanBeAnswered() {
        givenSubmissionState(chestPain);
        givenSaveReturnsItsArgument();

        TriageAnswerResponse response = service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "YES"))
                .answer();

        assertThat(response.questionKey()).isEqualTo("CHEST_PAIN");
        assertThat(response.answer()).isEqualTo("YES");
        ArgumentCaptor<TriageAnswer> saved = ArgumentCaptor.forClass(TriageAnswer.class);
        verify(triageAnswerRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getAnsweredAt()).isEqualTo(LocalDateTime.of(2026, 9, 29, 12, 0));
    }

    @Test
    void futureQuestionCannotBeAnswered() {
        givenSubmissionState(fever);

        assertThatThrownBy(() -> service().submitAnswer(SESSION_ID, new TriageAnswerRequest(2L, "NO")))
                .isInstanceOf(QuestionNotAnswerableException.class)
                .hasMessageContaining("FEVER")
                .hasMessageContaining("current question is CHEST_PAIN");

        verify(triageAnswerRepository, never()).save(any());
    }

    @Test
    void lockedFollowUpCannotBeAnswered() {
        givenSubmissionState(breathing, answer(chestPain, "NO"));

        assertThatThrownBy(() -> service().submitAnswer(SESSION_ID, new TriageAnswerRequest(4L, "YES")))
                .isInstanceOf(QuestionNotAnswerableException.class)
                .hasMessageContaining("depends on the answer to CHEST_PAIN");

        verify(triageAnswerRepository, never()).save(any());
    }

    @Test
    void followUpCanBeAnsweredOnceItsParentUnlocksIt() {
        givenSubmissionState(breathing, answer(chestPain, "YES"), answer(fever, "NO"));
        givenSaveReturnsItsArgument();

        TriageAnswerResponse response = service().submitAnswer(SESSION_ID, new TriageAnswerRequest(4L, "YES"))
                .answer();

        assertThat(response.answer()).isEqualTo("YES");
        verify(triageAnswerRepository, times(1)).save(any());
    }

    @Test
    void answeringAgainReplacesThePreviousAnswerEvenAfterMovingOn() {
        // FEVER is now the current question, but CHEST_PAIN may still be changed.
        TriageAnswer existing = answer(chestPain, "NO");
        givenSubmissionState(chestPain, existing);
        givenSaveReturnsItsArgument();

        service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "YES"));

        assertThat(existing.getAnswerValue()).isEqualTo("YES");
        verify(triageAnswerRepository, times(1)).save(existing);
    }

    @Test
    void inactiveQuestionIsRejectedAndNeverSaved() {
        fever.setActive(false);
        when(triageSessionService.getSessionForUpdate(SESSION_ID)).thenReturn(session);
        when(triageQuestionService.getQuestionById(2L)).thenReturn(fever);

        assertThatThrownBy(() -> service().submitAnswer(SESSION_ID, new TriageAnswerRequest(2L, "NO")))
                .isInstanceOf(QuestionNotAnswerableException.class)
                .hasMessageContaining("no longer asked");

        verify(triageAnswerRepository, never()).save(any());
    }

    // --- answer types -----------------------------------------------------

    @Test
    void yesNoAnswerIsStoredInCanonicalForm() {
        givenSubmissionState(chestPain);
        givenSaveReturnsItsArgument();

        TriageAnswerResponse response = service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, " yes "))
                .answer();

        assertThat(response.answer()).isEqualTo("YES");
    }

    @Test
    void numericAnswerIsStoredWithoutSurroundingSpace() {
        givenSubmissionState(temperature, answer(chestPain, "NO"), answer(fever, "YES"));
        givenSaveReturnsItsArgument();

        TriageAnswerResponse response = service().submitAnswer(SESSION_ID, new TriageAnswerRequest(5L, " 38.5 "))
                .answer();

        assertThat(response.answer()).isEqualTo("38.5");
    }

    @Test
    void answerThatDoesNotFitAYesNoQuestionIsRejectedBeforeSaving() {
        givenSubmissionState(chestPain);

        assertThatThrownBy(() -> service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "maybe")))
                .isInstanceOf(InvalidTriageAnswerException.class)
                .hasMessageContaining("CHEST_PAIN")
                .hasMessageContaining("YES or NO");

        verify(triageAnswerRepository, never()).save(any());
    }

    @Test
    void answerThatDoesNotFitANumberQuestionIsRejectedBeforeSaving() {
        givenSubmissionState(temperature, answer(chestPain, "NO"), answer(fever, "YES"));

        assertThatThrownBy(() -> service().submitAnswer(SESSION_ID, new TriageAnswerRequest(5L, "38,5")))
                .isInstanceOf(InvalidTriageAnswerException.class)
                .hasMessageContaining("TEMPERATURE");

        verify(triageAnswerRepository, never()).save(any());
    }

    @Test
    void rejectedAnswerLeavesAnEarlierValidAnswerUntouched() {
        TriageAnswer existing = answer(chestPain, "YES");
        givenSubmissionState(chestPain, existing);

        assertThatThrownBy(() -> service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "maybe")))
                .isInstanceOf(InvalidTriageAnswerException.class);

        assertThat(existing.getAnswerValue()).isEqualTo("YES");
        verify(triageAnswerRepository, never()).save(any());
    }

    @Test
    void answerToAnUnknownSessionIsRejectedBeforeAnythingIsSaved() {
        when(triageSessionService.getSessionForUpdate(SESSION_ID))
                .thenThrow(new TriageSessionNotFoundException(SESSION_ID));

        assertThatThrownBy(() -> service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "YES")))
                .isInstanceOf(TriageSessionNotFoundException.class);

        verify(triageAnswerRepository, never()).save(any());
    }

    // --- stale follow-up answers -------------------------------------------

    @Test
    void changingAParentAnswerRemovesTheFollowUpItClosed() {
        TriageAnswer parent = answer(chestPain, "YES");
        TriageAnswer unrelated = answer(fever, "NO");
        TriageAnswer staleChild = answer(breathing, "YES");
        givenSubmissionState(chestPain, parent, unrelated, staleChild);
        givenSaveReturnsItsArgument();

        service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "NO"));

        assertThat(parent.getAnswerValue()).isEqualTo("NO");
        verify(triageAnswerRepository).deleteAll(List.of(staleChild));
    }

    @Test
    void answerThatKeepsThePathIntactRemovesNothing() {
        givenSubmissionState(fever, answer(chestPain, "YES"));
        givenSaveReturnsItsArgument();

        service().submitAnswer(SESSION_ID, new TriageAnswerRequest(2L, "NO"));

        verify(triageAnswerRepository, never()).deleteAll(anyIterable());
    }

    @Test
    void changingTheParentBackReopensTheFollowUp() {
        // After CHEST_PAIN went YES -> NO, BREATHING's answer was removed. Now the
        // patient changes CHEST_PAIN back to YES: the follow-up must be asked again.
        TriageAnswer parent = answer(chestPain, "NO");
        List<TriageAnswer> answers = new ArrayList<>(List.of(parent, answer(fever, "NO")));
        when(triageSessionService.getSessionForUpdate(SESSION_ID)).thenReturn(session);
        when(triageSessionService.getSessionById(SESSION_ID)).thenReturn(session);
        when(triageQuestionService.getQuestionById(1L)).thenReturn(chestPain);
        when(triageQuestionService.getActiveQuestions()).thenReturn(bank);
        when(triageAnswerRepository.findBySessionIdWithQuestion(SESSION_ID)).thenReturn(answers);
        givenSaveReturnsItsArgument();

        service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "YES"));

        verify(triageAnswerRepository, never()).deleteAll(anyIterable());
        assertThat(service().findNextQuestion(SESSION_ID)).contains(breathing);
    }

    // --- created vs replaced, inputs version, audit ------------------------

    @Test
    void firstAnswerIsCreatedAndChangesTheSessionInputs() {
        givenSubmissionState(chestPain);
        givenSaveReturnsItsArgument();

        TriageAnswerService.SubmittedAnswer submitted =
                service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "YES"));

        assertThat(submitted.created()).isTrue();
        assertThat(session.getInputsVersion()).isEqualTo(1);
        verify(triageAuditLog).answerRecorded(session, "CHEST_PAIN", "YES");
    }

    @Test
    void changingAnAnswerReplacesItAndChangesTheSessionInputs() {
        givenSubmissionState(chestPain, answer(chestPain, "NO"));
        givenSaveReturnsItsArgument();

        TriageAnswerService.SubmittedAnswer submitted =
                service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "YES"));

        assertThat(submitted.created()).isFalse();
        assertThat(session.getInputsVersion()).isEqualTo(1);
        verify(triageAuditLog).answerChanged(session, "CHEST_PAIN", "NO", "YES");
    }

    @Test
    void resubmittingTheSameAnswerChangesNothingThatMatters() {
        // "yes" normalises to the "YES" already stored: a result evaluated before
        // this must stay current, and there is nothing to audit.
        givenSubmissionState(chestPain, answer(chestPain, "YES"));
        givenSaveReturnsItsArgument();

        TriageAnswerService.SubmittedAnswer submitted =
                service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "yes"));

        assertThat(submitted.created()).isFalse();
        assertThat(session.getInputsVersion()).isZero();
        verifyNoInteractions(triageAuditLog);
    }

    @Test
    void closingABranchAuditsTheAnswerItRemoved() {
        givenSubmissionState(chestPain, answer(chestPain, "YES"), answer(fever, "NO"), answer(breathing, "YES"));
        givenSaveReturnsItsArgument();

        service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "NO"));

        verify(triageAuditLog).answerChanged(session, "CHEST_PAIN", "YES", "NO");
        verify(triageAuditLog).answerRemoved(session, "BREATHING_DIFFICULTY", "YES");
        assertThat(session.getInputsVersion()).isEqualTo(1);
    }

    @Test
    void rejectedAnswerChangesNothingAndIsNotAudited() {
        givenSubmissionState(chestPain, answer(chestPain, "YES"));

        assertThatThrownBy(() -> service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "maybe")))
                .isInstanceOf(InvalidTriageAnswerException.class);

        assertThat(session.getInputsVersion()).isZero();
        verifyNoInteractions(triageAuditLog);
    }

    @Test
    void submittingLocksTheSessionWhileFindingTheNextQuestionDoesNot() {
        // Submissions change the session and must be serialised; reading the next
        // question changes nothing and must not block behind them.
        givenSubmissionState(chestPain);
        givenSaveReturnsItsArgument();
        service().submitAnswer(SESSION_ID, new TriageAnswerRequest(1L, "YES"));
        verify(triageSessionService).getSessionForUpdate(SESSION_ID);

        when(triageSessionService.getSessionById(SESSION_ID)).thenReturn(session);
        service().findNextQuestion(SESSION_ID);
        verify(triageSessionService, times(1)).getSessionForUpdate(SESSION_ID);
    }

    // --- helpers -----------------------------------------------------------

    private TriageAnswerService service() {
        return new TriageAnswerService(triageAnswerRepository, triageSessionService, triageQuestionService,
                triageAuditLog, FIXED_CLOCK);
    }

    private void givenActiveQuestions(TriageQuestion... questions) {
        when(triageSessionService.getSessionById(SESSION_ID)).thenReturn(session);
        when(triageQuestionService.getActiveQuestions()).thenReturn(List.of(questions));
    }

    private void givenAnswers(TriageAnswer... answers) {
        when(triageAnswerRepository.findBySessionIdWithQuestion(SESSION_ID)).thenReturn(List.of(answers));
    }

    /** Everything submitAnswer loads: the locked session, the question, the bank, and the answers so far. */
    private void givenSubmissionState(TriageQuestion question, TriageAnswer... answers) {
        when(triageSessionService.getSessionForUpdate(SESSION_ID)).thenReturn(session);
        when(triageQuestionService.getQuestionById(question.getId())).thenReturn(question);
        when(triageQuestionService.getActiveQuestions()).thenReturn(bank);
        givenAnswers(answers);
    }

    private void givenSaveReturnsItsArgument() {
        when(triageAnswerRepository.save(any(TriageAnswer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TriageAnswer answer(TriageQuestion question, String value) {
        return new TriageAnswer(session, question, value, LocalDateTime.of(2026, 9, 29, 10, 5));
    }

    /**
     * Ids are assigned by the database, and the entity deliberately has no
     * setter, so tests set them reflectively.
     */
    private static TriageQuestion question(Long id, String key, QuestionAnswerType answerType,
            String dependsOnKey, String dependsOnAnswer) {
        TriageQuestion question = new TriageQuestion(key, key + "?", answerType, dependsOnKey, dependsOnAnswer);
        ReflectionTestUtils.setField(question, "id", id);
        return question;
    }
}
