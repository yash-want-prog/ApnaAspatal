package com.ApnaAspatal.portal.triage.engine;

import java.util.List;
import java.util.Objects;

import com.ApnaAspatal.portal.triage.TriageDepartment;
import com.ApnaAspatal.portal.triage.TriageRiskLevel;

/**
 * The outcome of evaluating a triage session: how urgent, where to go, and why.
 *
 * <p>An immutable value, validated on construction, so an incomplete decision
 * cannot exist. A risk level with no stated reason could not be reviewed by a
 * clinician, so at least one reason code is mandatory.
 *
 * <p>Deliberately carries no timestamp and no session: the engine decides what
 * the answer is, and the caller records when and for whom.
 *
 * @param riskLevel             how urgent the case is
 * @param recommendedDepartment where the patient should be directed
 * @param reasonCodes           the rules that fired, in the order they fired;
 *                              read-only
 */
public record TriageDecision(
        TriageRiskLevel riskLevel,
        TriageDepartment recommendedDepartment,
        List<String> reasonCodes) {

    /**
     * Separator used when reason codes are flattened into a single stored string.
     * A code containing it would be split into two codes when read back, so codes
     * may not contain it.
     */
    public static final String REASON_CODE_SEPARATOR = ",";

    public TriageDecision {
        Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        Objects.requireNonNull(recommendedDepartment, "recommendedDepartment must not be null");
        Objects.requireNonNull(reasonCodes, "reasonCodes must not be null");

        // Defensive, unmodifiable copy. Also rejects null elements.
        reasonCodes = List.copyOf(reasonCodes);

        if (reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("reasonCodes must contain at least one code");
        }
        for (String code : reasonCodes) {
            if (code.isBlank()) {
                throw new IllegalArgumentException("reasonCodes must not contain a blank code");
            }
            if (code.contains(REASON_CODE_SEPARATOR)) {
                throw new IllegalArgumentException(
                        "reason code must not contain '" + REASON_CODE_SEPARATOR + "': " + code);
            }
        }
    }
}
