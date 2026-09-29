package com.ApnaAspatal.portal.triage.audit;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.ApnaAspatal.portal.auth.CurrentUser;
import com.ApnaAspatal.portal.triage.TriageSession;

/**
 * Records triage events. Call it inside the same transaction as the change it
 * describes, so the change and its record commit or roll back together - there
 * is never an event for a change that did not happen, or a change with no event.
 *
 * <p>The actor is the signed-in user and the time comes from the application
 * clock; callers supply only what happened.
 */
@Component
public class TriageAuditLog {

    private final TriageAuditEventRepository triageAuditEventRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public TriageAuditLog(TriageAuditEventRepository triageAuditEventRepository, CurrentUser currentUser,
            Clock clock) {
        this.triageAuditEventRepository = triageAuditEventRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    public void answerRecorded(TriageSession session, String questionKey, String value) {
        record(session, TriageAuditEventType.ANSWER_RECORDED, questionKey, null, value);
    }

    public void answerChanged(TriageSession session, String questionKey, String previousValue, String newValue) {
        record(session, TriageAuditEventType.ANSWER_CHANGED, questionKey, previousValue, newValue);
    }

    public void answerRemoved(TriageSession session, String questionKey, String previousValue) {
        record(session, TriageAuditEventType.ANSWER_REMOVED, questionKey, previousValue, null);
    }

    public void symptomAdded(TriageSession session, String symptomName, String description) {
        record(session, TriageAuditEventType.SYMPTOM_ADDED, symptomName, null, description);
    }

    /**
     * @param previousOutcome the session's earlier result, or null on its first evaluation
     */
    public void evaluated(TriageSession session, String riskLevel, String previousOutcome, String newOutcome) {
        record(session, TriageAuditEventType.EVALUATED, riskLevel, previousOutcome, newOutcome);
    }

    private void record(TriageSession session, TriageAuditEventType type, String subject,
            String previousValue, String newValue) {
        triageAuditEventRepository.save(new TriageAuditEvent(session, type, subject, previousValue, newValue,
                session.getInputsVersion(), currentUser.id(), LocalDateTime.now(clock)));
    }
}
