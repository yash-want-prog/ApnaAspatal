package com.ApnaAspatal.portal.triage;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ApnaAspatal.portal.triage.audit.TriageAuditLog;
import com.ApnaAspatal.portal.triage.dto.TriageAnswerRequest;
import com.ApnaAspatal.portal.triage.dto.TriageAnswerResponse;
import com.ApnaAspatal.portal.triage.question.QuestionAnswerType;
import com.ApnaAspatal.portal.triage.question.TriageQuestion;
import com.ApnaAspatal.portal.triage.question.TriageQuestionService;

/**
 * Recording answers, and choosing what to ask next.
 *
 * <p>The adaptive rules themselves live in {@link QuestionFlow}; this class
 * loads the session's state, applies them, and persists the outcome.
 */
@Service
public class TriageAnswerService {

    private final TriageAnswerRepository triageAnswerRepository;
    private final TriageSessionService triageSessionService;
    private final TriageQuestionService triageQuestionService;
    private final TriageAuditLog triageAuditLog;
    private final Clock clock;

    public TriageAnswerService(TriageAnswerRepository triageAnswerRepository,
            TriageSessionService triageSessionService,
            TriageQuestionService triageQuestionService,
            TriageAuditLog triageAuditLog,
            Clock clock) {
        this.triageAnswerRepository = triageAnswerRepository;
        this.triageSessionService = triageSessionService;
        this.triageQuestionService = triageQuestionService;
        this.triageAuditLog = triageAuditLog;
        this.clock = clock;
    }

    /**
     * A recorded answer, and whether it was newly created or replaced an earlier
     * answer to the same question - which decides between HTTP 201 and 200.
     */
    public record SubmittedAnswer(TriageAnswerResponse answer, boolean created) {
    }

    /**
     * Records an answer to one question in one session.
     *
     * <p>Only a question on the session's current path can be answered: the
     * question that is currently due, or one already answered whose branch is
     * still open - changing an earlier answer is allowed. The answer must fit
     * the question's {@link QuestionAnswerType} and is stored in canonical form,
     * so "yes" is stored as "YES". Answering again replaces the previous answer.
     *
     * <p>If the change closes a branch, answers inside that branch are no longer
     * reachable and are removed, so stored answers always describe the session's
     * current path.
     *
     * <p>One transaction: the checks, the write, and the cleanup succeed or fail
     * together, so a failure cannot leave a changed parent answer beside the
     * follow-up answers it invalidated. The session row is locked first, so two
     * submissions to the same session run one after the other and each sees the
     * other's result - neither can decide answerability from a stale path.
     *
     * <p>When the answers actually change, the session's inputs version goes up
     * (making any earlier result stale) and the change is recorded in the audit
     * log. Resubmitting an identical answer changes neither.
     *
     * @throws TriageSessionNotFoundException if the session does not exist or is not the user's
     * @throws com.ApnaAspatal.portal.triage.question.TriageQuestionNotFoundException if the question does not exist
     * @throws QuestionNotAnswerableException if the question is inactive, locked, or not yet due
     * @throws InvalidTriageAnswerException if the answer does not fit the question's type
     */
    @Transactional
    public SubmittedAnswer submitAnswer(Long sessionId, TriageAnswerRequest request) {
        TriageSession session = triageSessionService.getSessionForUpdate(sessionId);
        TriageQuestion question = triageQuestionService.getQuestionById(request.questionId());

        if (!question.isActive()) {
            throw QuestionNotAnswerableException.inactive(question.getQuestionKey());
        }

        List<TriageQuestion> activeQuestions = triageQuestionService.getActiveQuestions();
        List<TriageAnswer> answers = triageAnswerRepository.findBySessionIdWithQuestion(sessionId);
        requireAnswerable(new QuestionFlow(activeQuestions, answers), question);

        // Checked before anything is written: an invalid answer never replaces a
        // valid one already on record.
        String answerValue = question.getAnswerType().normalise(request.answer())
                .orElseThrow(() -> new InvalidTriageAnswerException(
                        question.getQuestionKey(), question.getAnswerType()));

        LocalDateTime answeredAt = LocalDateTime.now(clock);
        Optional<TriageAnswer> existing = answers.stream()
                .filter(answer -> answer.getQuestion().getQuestionKey().equals(question.getQuestionKey()))
                .findFirst();
        String previousValue = existing.map(TriageAnswer::getAnswerValue).orElse(null);

        TriageAnswer answer = existing
                .orElseGet(() -> new TriageAnswer(session, question, answerValue, answeredAt));
        answer.setAnswerValue(answerValue);
        answer.setAnsweredAt(answeredAt);
        TriageAnswer saved = triageAnswerRepository.save(answer);

        List<TriageAnswer> answersNow = new ArrayList<>(answers);
        if (existing.isEmpty()) {
            answersNow.add(saved);
        }
        List<TriageAnswer> unreachable = new QuestionFlow(activeQuestions, answersNow).unreachableAnswers();
        if (!unreachable.isEmpty()) {
            triageAnswerRepository.deleteAll(unreachable);
        }

        boolean valueChanged = !answerValue.equals(previousValue);
        if (valueChanged || !unreachable.isEmpty()) {
            session.recordInputsChanged();
            recordAnswerChange(session, question.getQuestionKey(), previousValue, answerValue, unreachable);
        }

        TriageAnswerResponse response = new TriageAnswerResponse(
                saved.getId(),
                session.getId(),
                question.getId(),
                question.getQuestionKey(),
                question.getQuestionText(),
                saved.getAnswerValue());
        return new SubmittedAnswer(response, existing.isEmpty());
    }

    /**
     * The first active, unlocked question this session has not answered yet, or
     * empty when the session has reached the end of its path.
     *
     * @throws TriageSessionNotFoundException if the session does not exist
     */
    public Optional<TriageQuestion> findNextQuestion(Long sessionId) {
        triageSessionService.getSessionById(sessionId);

        return new QuestionFlow(
                triageQuestionService.getActiveQuestions(),
                triageAnswerRepository.findBySessionIdWithQuestion(sessionId))
                .nextQuestion();
    }

    private void recordAnswerChange(TriageSession session, String questionKey, String previousValue,
            String newValue, List<TriageAnswer> removed) {
        if (previousValue == null) {
            triageAuditLog.answerRecorded(session, questionKey, newValue);
        } else if (!previousValue.equals(newValue)) {
            triageAuditLog.answerChanged(session, questionKey, previousValue, newValue);
        }
        for (TriageAnswer stale : removed) {
            triageAuditLog.answerRemoved(session, stale.getQuestion().getQuestionKey(), stale.getAnswerValue());
        }
    }

    /**
     * A question may be answered if its branch is open, and it is either the
     * question currently due or one already answered - which lets a patient change
     * an earlier answer after moving on.
     */
    private static void requireAnswerable(QuestionFlow flow, TriageQuestion question) {
        if (!flow.isUnlocked(question)) {
            throw QuestionNotAnswerableException.locked(
                    question.getQuestionKey(), question.getDependsOnQuestionKey());
        }
        if (flow.isAnswered(question)) {
            return;
        }

        Optional<TriageQuestion> due = flow.nextQuestion();
        boolean isDue = due.map(next -> next.getQuestionKey().equals(question.getQuestionKey())).orElse(false);
        if (!isDue) {
            throw QuestionNotAnswerableException.notYetDue(
                    question.getQuestionKey(), due.map(TriageQuestion::getQuestionKey));
        }
    }
}
