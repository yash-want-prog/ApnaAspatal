package com.ApnaAspatal.portal.triage;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ApnaAspatal.portal.triage.dto.TriageSessionRequest;
import com.ApnaAspatal.portal.triage.dto.TriageSessionResponse;

/**
 * HTTP entry point for triage sessions.
 *
 * <p>The {@code TriageSession} entity never leaves this class.
 */
@RestController
@RequestMapping("/api/triage-sessions")
public class TriageSessionController {

    private final TriageSessionService triageSessionService;

    public TriageSessionController(TriageSessionService triageSessionService) {
        this.triageSessionService = triageSessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TriageSessionResponse start(@Valid @RequestBody TriageSessionRequest request) {
        return toResponse(triageSessionService.startSession(request.patientId()));
    }

    /**
     * Maps the persistence type to the API type. The patient is flattened to its
     * id, which a lazy proxy can supply without being initialised.
     */
    private static TriageSessionResponse toResponse(TriageSession session) {
        return new TriageSessionResponse(
                session.getId(),
                session.getPatient().getId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getCompletedAt());
    }
}
