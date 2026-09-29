package com.ApnaAspatal.portal.triage;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.ApnaAspatal.portal.auth.CurrentUser;
import com.ApnaAspatal.portal.patient.Patient;
import com.ApnaAspatal.portal.patient.PatientNotFoundException;
import com.ApnaAspatal.portal.patient.PatientService;

/**
 * Business rules for triage sessions.
 *
 * <p>Every lookup is scoped to the signed-in user: a session belonging to another
 * account's patient is reported as not found, exactly like one that does not
 * exist.
 */
@Service
public class TriageSessionService {

    private final TriageSessionRepository triageSessionRepository;
    private final PatientService patientService;
    private final CurrentUser currentUser;
    private final Clock clock;

    public TriageSessionService(TriageSessionRepository triageSessionRepository,
            PatientService patientService,
            CurrentUser currentUser,
            Clock clock) {
        this.triageSessionRepository = triageSessionRepository;
        this.patientService = patientService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Opens a new triage session for one of the signed-in user's patients.
     *
     * <p>The session always starts {@code IN_PROGRESS} with no completion time -
     * those are server decisions, not client input.
     *
     * @throws PatientNotFoundException if the patient does not exist or is not the user's
     */
    public TriageSession startSession(Long patientId) {
        Patient patient = patientService.getPatientById(patientId);

        TriageSession session = new TriageSession(
                patient,
                TriageSessionStatus.IN_PROGRESS,
                LocalDateTime.now(clock));

        return triageSessionRepository.save(session);
    }

    /**
     * Resolves one of the signed-in user's sessions. The single place
     * session-not-found is decided.
     *
     * @throws TriageSessionNotFoundException if the session does not exist or is not the user's
     */
    public TriageSession getSessionById(Long sessionId) {
        return triageSessionRepository.findOwned(sessionId, currentUser.id())
                .orElseThrow(() -> new TriageSessionNotFoundException(sessionId));
    }

    /**
     * Like {@link #getSessionById(Long)}, but locks the session row until the
     * surrounding transaction ends, so concurrent changes to the same session run
     * one after another instead of interleaving. Must be called inside a
     * read-write transaction.
     *
     * @throws TriageSessionNotFoundException if the session does not exist or is not the user's
     */
    public TriageSession getSessionForUpdate(Long sessionId) {
        return triageSessionRepository.findOwnedForUpdate(sessionId, currentUser.id())
                .orElseThrow(() -> new TriageSessionNotFoundException(sessionId));
    }
}
