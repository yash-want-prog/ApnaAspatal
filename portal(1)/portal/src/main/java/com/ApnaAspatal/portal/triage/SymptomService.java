package com.ApnaAspatal.portal.triage;

import org.springframework.stereotype.Service;

import com.ApnaAspatal.portal.triage.dto.SymptomRequest;

/**
 * Business rules for symptoms recorded during a triage session.
 */
@Service
public class SymptomService {

    private final SymptomRepository symptomRepository;
    private final TriageSessionService triageSessionService;

    public SymptomService(SymptomRepository symptomRepository,
            TriageSessionService triageSessionService) {
        this.symptomRepository = symptomRepository;
        this.triageSessionService = triageSessionService;
    }

    /**
     * Records a symptom against an existing triage session.
     *
     * @throws TriageSessionNotFoundException if no session exists with the given id
     */
    public Symptom addSymptom(Long sessionId, SymptomRequest request) {
        TriageSession session = triageSessionService.getSessionById(sessionId);

        Symptom symptom = new Symptom(
                session,
                request.name(),
                request.severity(),
                request.onset(),
                request.duration());

        return symptomRepository.save(symptom);
    }
}
