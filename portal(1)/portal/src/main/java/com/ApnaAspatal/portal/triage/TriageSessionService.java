package com.ApnaAspatal.portal.triage;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.ApnaAspatal.portal.patient.Patient;
import com.ApnaAspatal.portal.patient.PatientNotFoundException;
import com.ApnaAspatal.portal.patient.PatientService;

/**
 * Business rules for triage sessions.
 */
@Service
public class TriageSessionService {

    private final TriageSessionRepository triageSessionRepository;
    private final PatientService patientService;

    public TriageSessionService(TriageSessionRepository triageSessionRepository,
            PatientService patientService) {
        this.triageSessionRepository = triageSessionRepository;
        this.patientService = patientService;
    }

    /**
     * Opens a new triage session for a patient.
     *
     * <p>The session always starts {@code IN_PROGRESS} with no completion time -
     * those are server decisions, not client input.
     *
     * @throws PatientNotFoundException if no patient exists with the given id
     */
    public TriageSession startSession(Long patientId) {
        Patient patient = patientService.getPatientById(patientId);

        TriageSession session = new TriageSession(
                patient,
                TriageSessionStatus.IN_PROGRESS,
                LocalDateTime.now());

        return triageSessionRepository.save(session);
    }

    /**
     * Resolves a session by id. The single place session-not-found is decided.
     *
     * @throws TriageSessionNotFoundException if no session exists with the given id
     */
    public TriageSession getSessionById(Long sessionId) {
        return triageSessionRepository.findById(sessionId)
                .orElseThrow(() -> new TriageSessionNotFoundException(sessionId));
    }
}
