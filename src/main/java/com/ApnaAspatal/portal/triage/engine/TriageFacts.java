package com.ApnaAspatal.portal.triage.engine;

import java.time.Period;
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
 * <p>Age arrives already computed. The caller derives it from the patient's date
 * of birth and the evaluation date; the engine never reads the clock, so the
 * same facts always describe the same patient.
 *
 * @param patientAge           the patient's age at evaluation, as years, months
 *                             and days; normalised, never negative
 * @param answersByQuestionKey each answered question's {@code questionKey} mapped
 *                             to the answer given
 * @param symptoms             the symptoms reported in the session
 */
public record TriageFacts(
        Period patientAge,
        Map<String, String> answersByQuestionKey,
        List<SymptomFact> symptoms) {

    /**
     * Validates and defensively copies. Age is normalised so that, for example,
     * 30 months reads as 2 years 6 months rather than 0 years. {@code Map.copyOf}
     * and {@code List.copyOf} also reject null keys, values, and elements.
     */
    public TriageFacts {
        Objects.requireNonNull(patientAge, "patientAge must not be null");
        patientAge = patientAge.normalized();
        if (patientAge.isNegative()) {
            throw new IllegalArgumentException("patientAge must not be negative: " + patientAge);
        }

        Objects.requireNonNull(answersByQuestionKey, "answersByQuestionKey must not be null");
        Objects.requireNonNull(symptoms, "symptoms must not be null");
        answersByQuestionKey = Map.copyOf(answersByQuestionKey);
        symptoms = List.copyOf(symptoms);
    }

    /**
     * Completed years of age - the common case for adult rules. Use
     * {@link #patientAge()} directly where months matter, as they do for infants.
     */
    public int ageInYears() {
        return patientAge.getYears();
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
