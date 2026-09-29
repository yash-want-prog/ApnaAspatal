package com.ApnaAspatal.portal.triage;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ApnaAspatal.portal.triage.dto.TriageResultResponse;
import com.ApnaAspatal.portal.triage.engine.TriageDecision;

/**
 * HTTP entry point for evaluating a triage session and reading its result.
 *
 * <p>All decision-making stays in the rule engine; this class only translates
 * between HTTP and the result.
 */
@RestController
@RequestMapping("/api/triage-sessions/{sessionId}")
public class TriageEvaluationController {

    private static final Pattern REASON_CODE_SEPARATOR =
            Pattern.compile(Pattern.quote(TriageDecision.REASON_CODE_SEPARATOR));

    private final TriageResultService triageResultService;

    public TriageEvaluationController(TriageResultService triageResultService) {
        this.triageResultService = triageResultService;
    }

    /**
     * Evaluates the session's current answers and stores the result, replacing any
     * earlier result for the session. 200 rather than 201: evaluating again
     * updates the one result a session has instead of creating another.
     */
    @PostMapping("/evaluation")
    public TriageResultResponse evaluate(@PathVariable Long sessionId) {
        return toResponse(triageResultService.evaluate(sessionId));
    }

    /**
     * The session's stored result. {@code current} is false if answers or symptoms
     * changed after it was computed.
     */
    @GetMapping("/result")
    public TriageResultResponse result(@PathVariable Long sessionId) {
        return toResponse(triageResultService.getResult(sessionId));
    }

    private static TriageResultResponse toResponse(TriageResultService.Outcome outcome) {
        TriageResult result = outcome.result();
        return new TriageResultResponse(
                result.getId(),
                result.getTriageSession().getId(),
                result.getRiskLevel(),
                result.getRecommendedDepartment(),
                List.of(REASON_CODE_SEPARATOR.split(result.getReasonCodes())),
                result.getEvaluatedAt(),
                outcome.current());
    }
}
