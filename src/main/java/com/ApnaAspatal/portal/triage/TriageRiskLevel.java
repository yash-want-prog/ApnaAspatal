package com.ApnaAspatal.portal.triage;

/**
 * How urgent a completed triage session is judged to be.
 *
 * <p>Ordered from least to most urgent. Stored as a string, so the constants may
 * be reordered safely; renaming one is a data migration.
 */
public enum TriageRiskLevel {

    LOW,
    MODERATE,
    HIGH
}
