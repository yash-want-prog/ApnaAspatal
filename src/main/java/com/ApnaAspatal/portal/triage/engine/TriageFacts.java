package com.ApnaAspatal.portal.triage.engine;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.ApnaAspatal.portal.triage.SymptomOnset;
import com.ApnaAspatal.portal.triage.SymptomSeverity;

/**
 * Everything the rule engine may reason about for one triage session.
 *
 * <p>An immutable snapshot, detached from JPA. The engine receives plain values
 * rather than entities so that it cannot trigger lazy loading, cannot mutate
 * managed state, and can be exercised in a unit test without a database.
 *
 * @param answersByQuestionKey each answered question's {@code questionKey} mapped to the answer given
 * @param symptoms             the symptoms reported in the session
 */
public record TriageFacts(
        Map<String, String> answersByQuestionKey,
        List<SymptomFact> symptoms) {

    /**
     * Defensive copies: callers cannot change the facts after the engine has
     * been handed them. {@code Map.copyOf} and {@code List.copyOf} also reject
     * null keys, values, and elements.
     */
    public TriageFacts {
        Objects.requireNonNull(answersByQuestionKey, "answersByQuestionKey must not be null");
        Objects.requireNonNull(symptoms, "symptoms must not be null");
        answersByQuestionKey = Map.copyOf(answersByQuestionKey);
        symptoms = List.copyOf(symptoms);
    }

    /**
     * The answer given to a question, if it was asked.
     */
    public Optional<String> answerFor(String questionKey) {
        return Optional.ofNullable(answersByQuestionKey.get(questionKey));
    }

    /**
     * Whether a question was answered with the expected value.
     *
     * <p>Case-insensitive, matching how the adaptive question engine compares
     * answers. Rules should use this rather than comparing strings themselves:
     * one rule using a case-sensitive {@code equals} would silently miss
     * {@code "yes"} and under-triage the patient.
     */
    public boolean answerIs(String questionKey, String expectedAnswer) {
        return answerFor(questionKey)
                .map(answer -> answer.equalsIgnoreCase(expectedAnswer))
                .orElse(false);
    }

    /**
     * A reported symptom, reduced to what a rule can reason about.
     *
     * @param duration free text as reported; {@code null} when the patient did not say
     */
    public record SymptomFact(
            String name,
            SymptomSeverity severity,
            SymptomOnset onset,
            String duration) {

        public SymptomFact {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(severity, "severity must not be null");
            Objects.requireNonNull(onset, "onset must not be null");
        }
    }
}
