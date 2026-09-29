package com.ApnaAspatal.portal.triage;

/**
 * How a symptom began.
 *
 * <p>{@code UNKNOWN} is a real clinical answer, not a missing value: a patient
 * may genuinely not know how a symptom started.
 */
public enum SymptomOnset {

    SUDDEN,
    GRADUAL,
    UNKNOWN
}
