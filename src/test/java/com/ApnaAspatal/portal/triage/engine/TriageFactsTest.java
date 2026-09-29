package com.ApnaAspatal.portal.triage.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ApnaAspatal.portal.triage.SymptomOnset;
import com.ApnaAspatal.portal.triage.SymptomSeverity;
import com.ApnaAspatal.portal.triage.engine.TriageFacts.SymptomFact;

/**
 * The facts handed to the engine must be immutable, must describe age without
 * reference to the clock, and must match answers the same way the adaptive
 * question engine does.
 */
class TriageFactsTest {

    private static final Period ADULT = Period.ofYears(40);

    // --- age ---------------------------------------------------------------

    @Test
    void exposesCompletedYearsOfAge() {
        TriageFacts facts = factsAged(Period.of(34, 5, 12));

        assertThat(facts.ageInYears()).isEqualTo(34);
    }

    @Test
    void ageIsFixedByTheFactsNotByWhenTheEngineRuns() {
        // One day short of a 65th birthday is 64 - today, tomorrow, and in ten
        // years. The engine never recomputes age from the clock.
        TriageFacts facts = factsAged(Period.of(64, 11, 30));

        assertThat(facts.ageInYears()).isEqualTo(64);
    }

    @Test
    void keepsMonthPrecisionForInfants() {
        TriageFacts twoMonths = factsAged(Period.ofMonths(2));
        TriageFacts tenMonths = factsAged(Period.ofMonths(10));

        // Both are "0 years", but they are not the same patient.
        assertThat(twoMonths.ageInYears()).isZero();
        assertThat(tenMonths.ageInYears()).isZero();
        assertThat(twoMonths.patientAge()).isNotEqualTo(tenMonths.patientAge());
        assertThat(twoMonths.patientAge().getMonths()).isEqualTo(2);
    }

    @Test
    void normalisesAgeSoMonthsCannotHideYears() {
        // Period.ofMonths(30).getYears() is 0 - without normalising, a 2.5-year-old
        // would read as under one.
        TriageFacts facts = factsAged(Period.ofMonths(30));

        assertThat(facts.ageInYears()).isEqualTo(2);
        assertThat(facts.patientAge()).isEqualTo(Period.of(2, 6, 0));
    }

    @Test
    void rejectsNegativeAge() {
        assertThatThrownBy(() -> factsAged(Period.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void requiresAge() {
        assertThatThrownBy(() -> new TriageFacts(null, Map.of(), List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("patientAge");
    }

    // --- answers -----------------------------------------------------------

    @Test
    void answerIsMatchesCaseInsensitively() {
        TriageFacts facts = new TriageFacts(ADULT, Map.of("CHEST_PAIN", "yes"), List.of());

        assertThat(facts.answerIs("CHEST_PAIN", "YES")).isTrue();
        assertThat(facts.answerIs("CHEST_PAIN", "NO")).isFalse();
    }

    @Test
    void unansweredQuestionNeverMatches() {
        TriageFacts facts = factsAged(ADULT);

        assertThat(facts.answerFor("FEVER")).isEmpty();
        assertThat(facts.answerIs("FEVER", "YES")).isFalse();
    }

    // --- immutability and determinism --------------------------------------

    @Test
    void laterChangesToTheCallersCollectionsDoNotLeakIn() {
        Map<String, String> answers = new HashMap<>(Map.of("FEVER", "NO"));
        List<SymptomFact> symptoms = new ArrayList<>();

        TriageFacts facts = new TriageFacts(ADULT, answers, symptoms);
        answers.put("FEVER", "YES");
        symptoms.add(new SymptomFact("Headache", SymptomSeverity.LOW, SymptomOnset.GRADUAL, null));

        assertThat(facts.answerIs("FEVER", "NO")).isTrue();
        assertThat(facts.symptoms()).isEmpty();
    }

    @Test
    void factsCannotBeModifiedThroughTheirAccessors() {
        TriageFacts facts = new TriageFacts(ADULT, Map.of("FEVER", "NO"), List.of());

        assertThatThrownBy(() -> facts.answersByQuestionKey().put("FEVER", "YES"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void identicalInputsProduceEqualFacts() {
        // The engine is a function of its facts; equal facts must be
        // indistinguishable, or "same input, same output" means nothing.
        TriageFacts first = new TriageFacts(Period.of(58, 2, 0), Map.of("CHEST_PAIN", "YES"),
                List.of(new SymptomFact("Chest pain", SymptomSeverity.SEVERE, SymptomOnset.SUDDEN, "2 hours")));
        TriageFacts second = new TriageFacts(Period.of(58, 2, 0), Map.of("CHEST_PAIN", "YES"),
                List.of(new SymptomFact("Chest pain", SymptomSeverity.SEVERE, SymptomOnset.SUDDEN, "2 hours")));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void rejectsNullCollections() {
        assertThatThrownBy(() -> new TriageFacts(ADULT, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TriageFacts(ADULT, Map.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- symptoms ----------------------------------------------------------

    @Test
    void symptomDurationMayBeUnknown() {
        SymptomFact symptom = new SymptomFact("Nausea", SymptomSeverity.LOW, SymptomOnset.UNKNOWN, null);

        assertThat(symptom.duration()).isNull();
    }

    @Test
    void symptomRequiresSeverity() {
        assertThatThrownBy(() -> new SymptomFact("Nausea", null, SymptomOnset.SUDDEN, "1 day"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("severity");
    }

    private static TriageFacts factsAged(Period age) {
        return new TriageFacts(age, Map.of(), List.of());
    }
}
