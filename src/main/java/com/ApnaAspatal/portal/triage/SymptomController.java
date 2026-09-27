package com.ApnaAspatal.portal.triage;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ApnaAspatal.portal.triage.dto.SymptomRequest;
import com.ApnaAspatal.portal.triage.dto.SymptomResponse;

/**
 * HTTP entry point for symptoms, nested under the session that owns them.
 *
 * <p>The {@code Symptom} entity never leaves this class.
 */
@RestController
@RequestMapping("/api/triage-sessions/{sessionId}/symptoms")
public class SymptomController {

    private final SymptomService symptomService;

    public SymptomController(SymptomService symptomService) {
        this.symptomService = symptomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SymptomResponse add(@PathVariable Long sessionId,
            @Valid @RequestBody SymptomRequest request) {
        return toResponse(symptomService.addSymptom(sessionId, request));
    }

    private static SymptomResponse toResponse(Symptom symptom) {
        return new SymptomResponse(
                symptom.getId(),
                symptom.getTriageSession().getId(),
                symptom.getName(),
                symptom.getSeverity(),
                symptom.getOnset(),
                symptom.getDuration());
    }
}
