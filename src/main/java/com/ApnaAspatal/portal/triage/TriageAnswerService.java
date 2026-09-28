package com.ApnaAspatal.portal.triage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ApnaAspatal.portal.triage.dto.TriageAnswerRequest;
import com.ApnaAspatal.portal.triage.dto.TriageAnswerResponse;
import com.ApnaAspatal.portal.triage.question.TriageQuestion;
import com.ApnaAspatal.portal.triage.question.TriageQuestionService;

/**
 * Recording answers, and choosing what to ask next.
 */
@Service
public class TriageAnswerService {

    private final TriageAnswerRepository triageAnswerRepository;
    private final TriageSessionService triageSessionService;
    private final TriageQuestionService triageQuestionService;

    public TriageAnswerService(TriageAnswerRepository triageAnswerRepository,
            TriageSessionService triageSessionService,
            TriageQuestionService triageQuestionService) {
        this.triageAnswerRepository = triageAnswerRepository;
        this.triageSessionService = triageSessionService;
        this.triageQuestionService = triageQuestionService;
    }

    /**
     * Records an answer to one question in one session.
     *
     * <p>A session answers each question at most once, so answering the same
     * question again replaces the previous answer rather than adding a second.
     *
     * @throws com.ApnaAspatal.portal.triage.TriageSessionNotFoundException if the session does not exist
     * @throws com.ApnaAspatal.portal.triage.question.TriageQuestionNotFoundException if the question does not exist
     */
    public TriageAnswerResponse submitAnswer(Long sessionId, TriageAnswerRequest request) {
        TriageSession session = triageSessionService.getSessionById(sessionId);
        TriageQuestion question = triageQuestionService.getQuestionById(request.questionId());

        TriageAnswer answer = triageAnswerRepository
                .findByTriageSessionIdAndQuestionId(sessionId, question.getId())
                .orElseGet(() -> new TriageAnswer(session, question, request.answer(), LocalDateTime.now()));

        answer.setAnswerValue(request.answer());
        answer.setAnsweredAt(LocalDateTime.now());

        TriageAnswer saved = triageAnswerRepository.save(answer);

        return new TriageAnswerResponse(
                saved.getId(),
                session.getId(),
                question.getId(),
                question.getQuestionKey(),
                question.getQuestionText(),
                saved.getAnswerValue());
    }

    /**
     * The first active question this session has not answered yet, or empty when
     * every active question has been answered.
     *
     * @throws com.ApnaAspatal.portal.triage.TriageSessionNotFoundException if the session does not exist
     */
    public Optional<TriageQuestion> findNextQuestion(Long sessionId) {
        triageSessionService.getSessionById(sessionId);

        List<TriageAnswer> answers = triageAnswerRepository.findBySessionIdWithQuestion(sessionId);

        Set<Long> answeredQuestionIds = answers.stream()
                .map(answer -> answer.getQuestion().getId())
                .collect(Collectors.toSet());

        Map<String, String> answersByQuestionKey = answers.stream()
                .collect(Collectors.toMap(
                        answer -> answer.getQuestion().getQuestionKey(),
                        TriageAnswer::getAnswerValue));

        List<TriageQuestion> activeQuestions = triageQuestionService.getActiveQuestions();

        return activeQuestions.stream()
                .filter(question -> !answeredQuestionIds.contains(question.getId()))
                .filter(question -> isEligible(question, answersByQuestionKey))
                .findFirst();
    }

    /**
     * Whether a question may be asked given the answers so far.
     *
     * <p>A question with no {@code dependsOnQuestionKey} is a root question and is
     * always eligible. Otherwise the prerequisite must have been answered, and -
     * when {@code dependsOnAnswer} is set - answered with that value.
     */
    private static boolean isEligible(TriageQuestion question, Map<String, String> answersByQuestionKey) {
        String dependsOnQuestionKey = question.getDependsOnQuestionKey();
        if (dependsOnQuestionKey == null || dependsOnQuestionKey.isBlank()) {
            return true;
        }

        String givenAnswer = answersByQuestionKey.get(dependsOnQuestionKey);
        if (givenAnswer == null) {
            return false;
        }

        String requiredAnswer = question.getDependsOnAnswer();
        if (requiredAnswer == null || requiredAnswer.isBlank()) {
            return true;
        }

        return requiredAnswer.equalsIgnoreCase(givenAnswer);
    }
}
