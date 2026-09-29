package com.ApnaAspatal.portal.triage.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.ApnaAspatal.portal.triage.TriageRiskLevel;

/**
 * Outgoing body for the evaluation and result endpoints.
 *
 * <p>Reason codes are returned as a list, not the comma-separated string they
 * are stored as - the storage format is not part of the API.
 *
 * @param current false once the session's answers or symptoms have changed since
 *                this result was computed; the session must be evaluated again
 *                before the result can be relied on
 */
public record TriageResultResponse(
        Long id,
        Long triageSessionId,
        TriageRiskLevel riskLevel,
        String recommendedDepartment,
        List<String> reasonCodes,
        LocalDateTime evaluatedAt,
        boolean current) {
}
