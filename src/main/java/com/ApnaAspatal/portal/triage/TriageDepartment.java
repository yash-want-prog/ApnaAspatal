package com.ApnaAspatal.portal.triage;

/**
 * Where a triaged patient should be directed.
 *
 * <p>Deliberately minimal. Each value must be justified by a rule that produces
 * it, so specialist departments are added alongside the clinical rules that
 * route to them - not in advance.
 *
 * <p>Persisted by name into {@code triage_results.recommended_department}. The
 * names are therefore a storage contract: constants may be added or reordered
 * freely, but renaming one is a data migration.
 */
public enum TriageDepartment {

    /** Urgent care: red-flag presentations that should not wait. */
    EMERGENCY,

    /** The default, non-urgent destination. */
    GENERAL_MEDICINE
}
