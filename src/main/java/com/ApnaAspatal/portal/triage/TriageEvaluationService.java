package com.ApnaAspatal.portal.triage;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ApnaAspatal.portal.patient.Patient;
import com.ApnaAspatal.portal.triage.engine.TriageDecision;
import com.ApnaAspatal.portal.triage.engine.TriageFacts;
import com.ApnaAspatal.portal.triage.engine.TriageFacts.SymptomFact;
import com.ApnaAspatal.portal.triage.engine.TriageRuleEngine;

/**
 * Evaluates a triage session: gathers its facts, asks the rule engine for a
 * decision, and records the result.
 *
 * <p>This class makes no medical judgements. Everything clinical happens inside
 * {@link TriageRuleEngine}; this class only translates between the persisted
 * domain and the engine's pure inputs and outputs.
 */
@Service
public class TriageEvaluationService {

    /**
     * Must equal the length of {@code triage_results.reason_codes}. Checked here
     * so an over-long decision is rejected with a clear message, rather than
     * failing first in the database.
     */
    static final int MAX_STORED_REASON_CODES_LENGTH = 500;

    private final TriageSessionService triageSessionService;
    private final TriageAnswerRepository triageAnswerRepository;
    private final SymptomRepository symptomRepository;
    private final TriageResultRepository triageResultRepository;
    private final TriageRuleEngine triageRuleEngine;
    private final Clock clock;

    public TriageEvaluationService(TriageSessionService triageSessionService,
            TriageAnswerRepository triageAnswerRepository,
            SymptomRepository symptomRepository,
            TriageResultRepository triageResultRepository,
            TriageRuleEngine triageRuleEngine,
            Clock clock) {
        this.triageSessionService = triageSessionService;
        this.triageAnswerRepository = triageAnswerRepository;
        this.symptomRepository = symptomRepository;
        this.triageResultRepository = triageResultRepository;
        this.triageRuleEngine = triageRuleEngine;
        this.clock = clock;
    }

    /**
     * Evaluates one session and stores the outcome, replacing any earlier result
     * for the same session.
     *
     * <p>One transaction covers the whole evaluation: the patient is lazily
     * loaded and must be read inside an open persistence context, and the
     * existing-result check and the write that follows must succeed or fail
     * together.
     *
     * @throws TriageSessionNotFoundException  if the session does not exist
     * @throws PatientAgeUnavailableException  if the patient's age cannot be determined
     * @throws IllegalStateException           if the engine returns no decision, or one
     *                                         whose reason codes cannot be stored
     */
    @Transactional
    public TriageResult evaluate(Long sessionId) {
        // One captured moment, in one explicit zone. The age and evaluatedAt are
        // both derived from it, so they can never disagree about the date.
        ZonedDateTime evaluationMoment = clock.instant().atZone(clock.getZone());

        TriageSession session = triageSessionService.getSessionById(sessionId);
        Period patientAge = ageOn(session.getPatient(), evaluationMoment.toLocalDate(), sessionId);

        TriageFacts facts = new TriageFacts(
                patientAge,
                answersByQuestionKey(sessionId),
                symptomFacts(sessionId));

        TriageDecision decision = triageRuleEngine.evaluate(facts);
        if (decision == null) {
            throw new IllegalStateException(
                    "Triage rule engine returned no decision for session " + sessionId);
        }

        String storedReasonCodes = toStoredReasonCodes(decision.reasonCodes(), sessionId);
        String storedDepartment = decision.recommendedDepartment().name();
        LocalDateTime evaluatedAt = evaluationMoment.toLocalDateTime();

        // One result per session: update the existing row rather than inserting a
        // second one, which the unique constraint would reject.
        TriageResult result = triageResultRepository.findByTriageSessionId(sessionId)
                .orElseGet(() -> new TriageResult(session, decision.riskLevel(),
                        storedDepartment, storedReasonCodes, evaluatedAt));

        result.setRiskLevel(decision.riskLevel());
        result.setRecommendedDepartment(storedDepartment);
        result.setReasonCodes(storedReasonCodes);
        result.setEvaluatedAt(evaluatedAt);

        return triageResultRepository.save(result);
    }

    private static Period ageOn(Patient patient, LocalDate evaluationDate, Long sessionId) {
        LocalDate dateOfBirth = patient.getDateOfBirth();
        if (dateOfBirth == null) {
            throw PatientAgeUnavailableException.missingDateOfBirth(sessionId, patient.getId());
        }
        if (dateOfBirth.isAfter(evaluationDate)) {
            throw PatientAgeUnavailableException.dateOfBirthInFuture(sessionId, patient.getId());
        }
        return Period.between(dateOfBirth, evaluationDate);
    }

    private Map<String, String> answersByQuestionKey(Long sessionId) {
        // At most one answer per question per session (unique constraint), so the
        // keys cannot collide.
        return triageAnswerRepository.findBySessionIdWithQuestion(sessionId).stream()
                .collect(Collectors.toMap(
                        answer -> answer.getQuestion().getQuestionKey(),
                        TriageAnswer::getAnswerValue));
    }

    private List<SymptomFact> symptomFacts(Long sessionId) {
        return symptomRepository.findByTriageSessionIdOrderByIdAsc(sessionId).stream()
                .map(symptom -> new SymptomFact(
                        symptom.getName(),
                        symptom.getSeverity(),
                        symptom.getOnset(),
                        symptom.getDuration()))
                .toList();
    }

    private static String toStoredReasonCodes(List<String> reasonCodes, Long sessionId) {
        String joined = String.join(TriageDecision.REASON_CODE_SEPARATOR, reasonCodes);
        if (joined.length() > MAX_STORED_REASON_CODES_LENGTH) {
            throw new IllegalStateException(
                    "Reason codes for session " + sessionId + " are " + joined.length()
                            + " characters; at most " + MAX_STORED_REASON_CODES_LENGTH + " can be stored");
        }
        return joined;
    }
}
