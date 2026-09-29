package com.ApnaAspatal.portal.triage;

/**
 * Lifecycle state of a {@link TriageSession}.
 *
 * <p>Stored as a string, not an ordinal - see the mapping on
 * {@code TriageSession.status}. Constants may therefore be reordered safely, but
 * renaming one is a data migration.
 */
public enum TriageSessionStatus {

    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
