package com.ApnaAspatal.portal.triage.audit;

/**
 * What happened to a triage session. Stored by name, so constants may be added
 * freely but never renamed.
 */
public enum TriageAuditEventType {

    /** A question was answered for the first time. */
    ANSWER_RECORDED,

    /** An existing answer was replaced with a different value. */
    ANSWER_CHANGED,

    /** An answer was removed because a changed earlier answer closed its branch. */
    ANSWER_REMOVED,

    /** A symptom was added to the session. */
    SYMPTOM_ADDED,

    /** The session was evaluated by the rule engine. */
    EVALUATED
}
