package com.ApnaAspatal.portal.triage.engine;

/**
 * The reason codes the rule engine can emit.
 *
 * <p>These are stored in {@code triage_results.reason_codes} and form the audit
 * trail explaining every decision, so they are a stable vocabulary: add codes
 * freely, but never rename or reuse one, or historical results change meaning.
 */
public final class TriageReasonCodes {

    public static final String CHEST_PAIN = "CHEST_PAIN";
    public static final String BREATHING_DIFFICULTY = "BREATHING_DIFFICULTY";
    public static final String SEVERE_SYMPTOM = "SEVERE_SYMPTOM";
    public static final String FEVER = "FEVER";
    public static final String ELEVATED_TEMPERATURE = "ELEVATED_TEMPERATURE";
    public static final String NO_HIGH_RISK_RULE_MATCHED = "NO_HIGH_RISK_RULE_MATCHED";
    public static final String INSUFFICIENT_INFORMATION = "INSUFFICIENT_INFORMATION";

    private TriageReasonCodes() {
    }
}
