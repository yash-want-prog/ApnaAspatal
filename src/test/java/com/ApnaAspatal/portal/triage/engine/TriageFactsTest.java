package com.ApnaAspatal.portal.triage.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ApnaAspatal.portal.triage.SymptomOnset;
import com.ApnaAspatal.portal.triage.SymptomSeverity;
import com.ApnaAspatal.portal.triage.engine.TriageFacts.SymptomFact;

/**
 * The facts handed to the engine must be immutable, and answer matching must
 * behave the same way the adaptive question engine does.
 */
class TriageFactsTest {

    @Test
    void answerIsMatchesCaseInsensitively() {
        TriageFacts facts = new TriageFacts(Map.of("CHEST_PAIN", "yes"), List.of());

        assertThat(facts.answerIs("CHEST_PAIN", "YES")).isTrue();
        assertThat(facts.answerIs("CHEST_PAIN", "NO")).isFalse();
    }

    @Test
    void unansweredQuestionNeverMatches() {
        TriageFacts facts = new TriageFacts(Map.of(), List.of());

        assertThat(facts.answerFor("FEVER")).isEmpty();
        assertThat(facts.answerIs("FEVER", "YES")).isFalse();
    }

    @Test
    void laterChangesToTheCallersCollectionsDoNotLeakIn() {
        Map<String, String> answers = new HashMap<>(Map.of("FEVER", "NO"));
        List<SymptomFact> symptoms = new ArrayList<>();

        TriageFacts facts = new TriageFacts(answers, symptoms);
        answers.put("FEVER", "YES");
        symptoms.add(new SymptomFact("Headache", SymptomSeverity.LOW, SymptomOnset.GRADUAL, null));

        assertThat(facts.answerIs("FEVER", "NO")).isTrue();
        assertThat(facts.symptoms()).isEmpty();
    }

    @Test
    void factsCannotBeModifiedThroughTheirAccessors() {
        TriageFacts facts = new TriageFacts(Map.of("FEVER", "NO"), List.of());

        assertThatThrownBy(() -> facts.answersByQuestionKey().put("FEVER", "YES"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullCollections() {
        assertThatThrownBy(() -> new TriageFacts(null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TriageFacts(Map.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

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
}
