package com.ApnaAspatal.portal.triage;

/**
 * How severe a reported symptom is.
 *
 * <p>Ordered from least to most severe. Stored as a string, so the constants may
 * be reordered safely; renaming one is a data migration.
 */
public enum SymptomSeverity {

    LOW,
    MODERATE,
    SEVERE
}
