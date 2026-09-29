package com.ApnaAspatal.portal.triage.engine;

import java.util.Objects;

import com.ApnaAspatal.portal.triage.TriageRiskLevel;

/**
 * The outcome of evaluating a triage session: how urgent, where to go, and why.
 *
 * <p>An immutable value, validated on construction, so an incomplete decision
 * cannot exist. A risk level with no stated reason could not be reviewed by a
 * clinician, so every field is mandatory.
 *
 * <p>Deliberately carries no timestamp and no session: the engine decides what
 * the answer is, and the caller records when and for whom.
 *
 * @param riskLevel             how urgent the case is
 * @param recommendedDepartment where the patient should be directed
 * @param reasonCodes           comma-separated codes naming the rules that fired
 */
public record TriageDecision(
        TriageRiskLevel riskLevel,
        String recommendedDepartment,
        String reasonCodes) {

    public TriageDecision {
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        requireNonBlank(recommendedDepartment, "recommendedDepartment");
        requireNonBlank(reasonCodes, "reasonCodes");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
