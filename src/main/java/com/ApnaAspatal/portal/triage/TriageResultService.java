package com.ApnaAspatal.portal.triage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ApnaAspatal.portal.triage.audit.TriageAuditLog;

/**
 * Evaluating a session and reading its result, with the result's currency made
 * explicit.
 *
 * <p>The decision itself stays entirely in {@link TriageEvaluationService} and the
 * rule engine behind it, which this class calls unchanged. What it adds around
 * that call is consistency:
 * <ul>
 *   <li>the session row is locked first, so an evaluation and an answer change
 *       to the same session cannot interleave;</li>
 *   <li>the result is stamped with the inputs version it was computed from, so a
 *       later answer or symptom change makes it visibly stale rather than
 *       silently wrong;</li>
 *   <li>the evaluation is recorded in the audit log.</li>
 * </ul>
 */
@Service
public class TriageResultService {

    private final TriageSessionService triageSessionService;
    private final TriageEvaluationService triageEvaluationService;
    private final TriageResultRepository triageResultRepository;
    private final TriageAuditLog triageAuditLog;

    public TriageResultService(TriageSessionService triageSessionService,
            TriageEvaluationService triageEvaluationService,
            TriageResultRepository triageResultRepository,
            TriageAuditLog triageAuditLog) {
        this.triageSessionService = triageSessionService;
        this.triageEvaluationService = triageEvaluationService;
        this.triageResultRepository = triageResultRepository;
        this.triageAuditLog = triageAuditLog;
    }

    /** A session's result, and whether it still reflects the session's current inputs. */
    public record Outcome(TriageResult result, boolean current) {
    }

    /**
     * Evaluates the session's current answers and symptoms, replacing any earlier
     * result. One transaction: {@code TriageEvaluationService} joins it, so the
     * lock taken here is held for the whole evaluation.
     *
     * @throws TriageSessionNotFoundException if the session does not exist or is not the user's
     * @throws PatientAgeUnavailableException if the patient's age cannot be determined
     */
    @Transactional
    public Outcome evaluate(Long sessionId) {
        TriageSession session = triageSessionService.getSessionForUpdate(sessionId);
        String previousOutcome = triageResultRepository.findByTriageSessionId(sessionId)
                .map(TriageResultService::describe)
                .orElse(null);

        TriageResult result = triageEvaluationService.evaluate(sessionId);
        result.recordEvaluatedInputsVersion(session.getInputsVersion());

        triageAuditLog.evaluated(session, result.getRiskLevel().name(), previousOutcome, describe(result));
        return new Outcome(result, true);
    }

    /**
     * The session's stored result, marked stale if answers or symptoms have
     * changed since it was computed.
     *
     * @throws TriageSessionNotFoundException if the session does not exist or is not the user's
     * @throws TriageResultNotFoundException  if the session has never been evaluated
     */
    @Transactional(readOnly = true)
    public Outcome getResult(Long sessionId) {
        TriageSession session = triageSessionService.getSessionById(sessionId);
        TriageResult result = triageResultRepository.findByTriageSessionId(sessionId)
                .orElseThrow(() -> new TriageResultNotFoundException(sessionId));
        return new Outcome(result, result.isCurrentFor(session));
    }

    private static String describe(TriageResult result) {
        return "riskLevel=" + result.getRiskLevel()
                + "; department=" + result.getRecommendedDepartment()
                + "; reasonCodes=" + result.getReasonCodes();
    }
}
