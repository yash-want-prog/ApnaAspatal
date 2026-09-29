package com.ApnaAspatal.portal.triage.engine;

/**
 * Decides how urgent a triage session is, from facts alone.
 *
 * <p>Every implementation must honour this contract:
 * <ul>
 *   <li><b>Pure.</b> No database, repositories, network calls, AI models, or
 *       writes of any kind. Everything the decision depends on arrives in
 *       {@link TriageFacts}.</li>
 *   <li><b>Deterministic.</b> The same facts always produce the same decision.
 *       In particular, implementations must not read the clock - the caller
 *       records when an evaluation happened.</li>
 *   <li><b>Total.</b> Never returns {@code null}. When no specific rule applies,
 *       that is itself a clinical decision and must be returned explicitly, with
 *       a reason code saying so.</li>
 * </ul>
 *
 * <p>These properties are what make a decision reproducible and auditable: given
 * the facts that were logged, the same decision can always be re-derived.
 */
public interface TriageRuleEngine {

    /**
     * @param facts the snapshot to evaluate; never {@code null}
     * @return the decision; never {@code null}
     */
    TriageDecision evaluate(TriageFacts facts);
}
