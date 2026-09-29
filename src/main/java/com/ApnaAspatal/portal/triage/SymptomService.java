package com.ApnaAspatal.portal.triage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ApnaAspatal.portal.triage.audit.TriageAuditLog;
import com.ApnaAspatal.portal.triage.dto.SymptomRequest;

/**
 * Business rules for symptoms recorded during a triage session.
 */
@Service
public class SymptomService {

    private final SymptomRepository symptomRepository;
    private final TriageSessionService triageSessionService;
    private final TriageAuditLog triageAuditLog;

    public SymptomService(SymptomRepository symptomRepository,
            TriageSessionService triageSessionService,
            TriageAuditLog triageAuditLog) {
        this.symptomRepository = symptomRepository;
        this.triageSessionService = triageSessionService;
        this.triageAuditLog = triageAuditLog;
    }

    /**
     * Records a symptom against one of the signed-in user's sessions.
     *
     * <p>Symptoms are rule-engine inputs, so adding one makes any existing result
     * stale. One transaction under the session lock: the symptom, the version
     * change, and the audit record commit together.
     *
     * @throws TriageSessionNotFoundException if the session does not exist or is not the user's
     */
    @Transactional
    public Symptom addSymptom(Long sessionId, SymptomRequest request) {
        TriageSession session = triageSessionService.getSessionForUpdate(sessionId);

        Symptom symptom = new Symptom(
                session,
                request.name(),
                request.severity(),
                request.onset(),
                request.duration());
        Symptom saved = symptomRepository.save(symptom);

        session.recordInputsChanged();
        triageAuditLog.symptomAdded(session, request.name(),
                "severity=" + request.severity() + "; onset=" + request.onset()
                        + "; duration=" + request.duration());
        return saved;
    }
}
