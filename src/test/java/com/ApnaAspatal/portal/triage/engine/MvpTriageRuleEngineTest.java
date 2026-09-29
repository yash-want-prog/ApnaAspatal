package com.ApnaAspatal.portal.triage.engine;

import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.BREATHING_DIFFICULTY;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.CHEST_PAIN;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.ELEVATED_TEMPERATURE;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.FEVER;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.INSUFFICIENT_INFORMATION;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.NO_HIGH_RISK_RULE_MATCHED;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.SEVERE_SYMPTOM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ApnaAspatal.portal.triage.SymptomOnset;
import com.ApnaAspatal.portal.triage.SymptomSeverity;
import com.ApnaAspatal.portal.triage.TriageDepartment;
import com.ApnaAspatal.portal.triage.TriageRiskLevel;
import com.ApnaAspatal.portal.triage.engine.TriageFacts.SymptomFact;

/**
 * The MVP rules, their priority, and the guarantee that missing or ambiguous
 * information is never treated as low risk.
 */
class MvpTriageRuleEngineTest {

    private static final TriageDecision HIGH_CHEST_PAIN_AND_BREATHING = new TriageDecision(
            TriageRiskLevel.HIGH, TriageDepartment.EMERGENCY, List.of(CHEST_PAIN, BREATHING_DIFFICULTY));

    private static final TriageDecision HIGH_SEVERE_CHEST_PAIN = new TriageDecision(
            TriageRiskLevel.HIGH, TriageDepartment.EMERGENCY, List.of(CHEST_PAIN, SEVERE_SYMPTOM));

    private static final TriageDecision MODERATE_FEVER = new TriageDecision(
            TriageRiskLevel.MODERATE, TriageDepartment.GENERAL_MEDICINE, List.of(FEVER, ELEVATED_TEMPERATURE));

    private static final TriageDecision LOW_NO_RULE_MATCHED = new TriageDecision(
            TriageRiskLevel.LOW, TriageDepartment.GENERAL_MEDICINE, List.of(NO_HIGH_RISK_RULE_MATCHED));

    private static final TriageDecision INSUFFICIENT = new TriageDecision(
            TriageRiskLevel.MODERATE, TriageDepartment.GENERAL_MEDICINE, List.of(INSUFFICIENT_INFORMATION));

    private final MvpTriageRuleEngine engine = new MvpTriageRuleEngine();

    // --- HIGH --------------------------------------------------------------

    @Test
    void chestPainWithBreathingDifficultyIsHighRisk() {
        // Fever was never asked. A positive red flag is acted on regardless.
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "YES", "BREATHING_DIFFICULTY", "YES"));

        assertThat(engine.evaluate(facts)).isEqualTo(HIGH_CHEST_PAIN_AND_BREATHING);
    }

    @Test
    void severeChestPainIsHighRisk() {
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "YES", "BREATHING_DIFFICULTY", "NO"),
                symptom("Chest pain", SymptomSeverity.SEVERE));

        assertThat(engine.evaluate(facts)).isEqualTo(HIGH_SEVERE_CHEST_PAIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Chest pain", "  CHEST PAIN  ", "chest_pain", "Chest-Pain", "chest   pain"})
    void recognisesChestPainSymptomDespiteCaseAndSeparators(String symptomName) {
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "YES", "BREATHING_DIFFICULTY", "NO"),
                symptom(symptomName, SymptomSeverity.SEVERE));

        assertThat(engine.evaluate(facts)).isEqualTo(HIGH_SEVERE_CHEST_PAIN);
    }

    @Test
    void matchesAnswersRegardlessOfCase() {
        // A case-sensitive comparison would miss these and under-triage.
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "yes", "BREATHING_DIFFICULTY", "Yes"));

        assertThat(engine.evaluate(facts)).isEqualTo(HIGH_CHEST_PAIN_AND_BREATHING);
    }

    // --- MODERATE ----------------------------------------------------------

    @Test
    void feverWithElevatedTemperatureIsModerate() {
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "NO", "FEVER", "YES", "TEMPERATURE", "38.5"));

        assertThat(engine.evaluate(facts)).isEqualTo(MODERATE_FEVER);
    }

    @Test
    void temperatureThresholdIsInclusive() {
        TriageFacts atThreshold = facts(Map.of("CHEST_PAIN", "NO", "FEVER", "YES", "TEMPERATURE", "38.0"));
        TriageFacts justBelow = facts(Map.of("CHEST_PAIN", "NO", "FEVER", "YES", "TEMPERATURE", "37.9"));

        assertThat(engine.evaluate(atThreshold)).isEqualTo(MODERATE_FEVER);
        assertThat(engine.evaluate(justBelow)).isEqualTo(LOW_NO_RULE_MATCHED);
    }

    // --- LOW ---------------------------------------------------------------

    @Test
    void symptomsWithNoMatchingRuleAreLowRisk() {
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "NO", "FEVER", "NO"),
                symptom("Headache", SymptomSeverity.MODERATE));

        assertThat(engine.evaluate(facts)).isEqualTo(LOW_NO_RULE_MATCHED);
    }

    @Test
    void feverWithNormalTemperatureIsLowRisk() {
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "NO", "FEVER", "YES", "TEMPERATURE", "37.2"));

        assertThat(engine.evaluate(facts)).isEqualTo(LOW_NO_RULE_MATCHED);
    }

    @Test
    void chestPainWithoutEitherRedFlagIsLowUnderTheMvpRules() {
        // Pins the specified behaviour: chest pain alone, not severe and without
        // breathing difficulty, matches no HIGH rule. See the report - this is a
        // known gap in the MVP rule set, not an endorsement.
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "YES", "BREATHING_DIFFICULTY", "NO", "FEVER", "NO"),
                symptom("Chest pain", SymptomSeverity.MODERATE));

        assertThat(engine.evaluate(facts)).isEqualTo(LOW_NO_RULE_MATCHED);
    }

    // --- INSUFFICIENT INFORMATION: never silently LOW ------------------------

    @Test
    void nothingAnsweredIsInsufficientNotLow() {
        assertThat(engine.evaluate(facts(Map.of()))).isEqualTo(INSUFFICIENT);
    }

    @Test
    void feverWithoutATemperatureIsInsufficient() {
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "NO", "FEVER", "YES"));

        assertThat(engine.evaluate(facts)).isEqualTo(INSUFFICIENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hot", "38,5", "38.5C", "NaN", "98.6", "101"})
    void unreadableTemperatureIsInsufficient(String temperature) {
        // "98.6" and "101" are Fahrenheit readings. Read as Celsius they would be
        // wrongly reported as elevated, so they are treated as unreadable instead.
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "NO", "FEVER", "YES", "TEMPERATURE", temperature));

        assertThat(engine.evaluate(facts)).isEqualTo(INSUFFICIENT);
    }

    @Test
    void chestPainWithoutABreathingAnswerIsInsufficient() {
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "YES", "FEVER", "NO"),
                symptom("Chest pain", SymptomSeverity.MODERATE));

        assertThat(engine.evaluate(facts)).isEqualTo(INSUFFICIENT);
    }

    @Test
    void chestPainWithoutARecordedSeverityIsInsufficient() {
        // Rule B cannot be ruled out without knowing how severe the chest pain is.
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "YES", "BREATHING_DIFFICULTY", "NO", "FEVER", "NO"));

        assertThat(engine.evaluate(facts)).isEqualTo(INSUFFICIENT);
    }

    @Test
    void unrecognisedAnswerIsTreatedAsMissingNotAsNo() {
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "maybe", "FEVER", "NO"));

        assertThat(engine.evaluate(facts)).isEqualTo(INSUFFICIENT);
    }

    @Test
    void chestPainDeniedButRecordedAsASymptomIsInsufficient() {
        // Contradictory information: picking either side could under-triage.
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "NO", "FEVER", "NO"),
                symptom("Chest pain", SymptomSeverity.SEVERE));

        assertThat(engine.evaluate(facts)).isEqualTo(INSUFFICIENT);
    }

    // --- priority ----------------------------------------------------------

    @Test
    void highRiskTakesPriorityOverModerate() {
        TriageFacts facts = facts(Map.of(
                "CHEST_PAIN", "YES", "BREATHING_DIFFICULTY", "YES",
                "FEVER", "YES", "TEMPERATURE", "39.5"));

        assertThat(engine.evaluate(facts)).isEqualTo(HIGH_CHEST_PAIN_AND_BREATHING);
    }

    @Test
    void ruleAIsCheckedBeforeRuleBWhenBothMatch() {
        TriageFacts facts = facts(Map.of("CHEST_PAIN", "YES", "BREATHING_DIFFICULTY", "YES"),
                symptom("Chest pain", SymptomSeverity.SEVERE));

        assertThat(engine.evaluate(facts)).isEqualTo(HIGH_CHEST_PAIN_AND_BREATHING);
    }

    @Test
    void moderateTakesPriorityOverInsufficientInformation() {
        // Chest pain was never answered, but a matched MODERATE rule wins as
        // specified. Both outcomes are MODERATE / GENERAL_MEDICINE.
        TriageFacts facts = facts(Map.of("FEVER", "YES", "TEMPERATURE", "39.0"));

        assertThat(engine.evaluate(facts)).isEqualTo(MODERATE_FEVER);
    }

    // --- determinism -------------------------------------------------------

    @Test
    void sameFactsAlwaysProduceTheSameDecision() {
        List<TriageFacts> cases = List.of(
                facts(Map.of("CHEST_PAIN", "YES", "BREATHING_DIFFICULTY", "YES")),
                facts(Map.of("CHEST_PAIN", "NO", "FEVER", "YES", "TEMPERATURE", "38.5")),
                facts(Map.of("CHEST_PAIN", "NO", "FEVER", "NO"), symptom("Headache", SymptomSeverity.LOW)),
                facts(Map.of()));

        for (TriageFacts facts : cases) {
            TriageDecision first = engine.evaluate(facts);

            // Again, on the same instance - no state carried between calls.
            assertThat(engine.evaluate(facts)).isEqualTo(first);
            // And on a fresh instance, from an equal but separately built copy.
            TriageFacts copy = new TriageFacts(facts.patientAge(), Map.copyOf(facts.answersByQuestionKey()),
                    List.copyOf(facts.symptoms()));
            assertThat(new MvpTriageRuleEngine().evaluate(copy)).isEqualTo(first);
        }
    }

    @Test
    void decisionDoesNotDependOnTheMachineLocale() {
        Locale original = Locale.getDefault();
        try {
            // In Turkish, "I".toLowerCase() is a dotless i, which would break a
            // naive match of "CHEST PAIN".
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TriageFacts facts = facts(Map.of("CHEST_PAIN", "YES", "BREATHING_DIFFICULTY", "NO"),
                    symptom("CHEST PAIN", SymptomSeverity.SEVERE));

            assertThat(engine.evaluate(facts)).isEqualTo(HIGH_SEVERE_CHEST_PAIN);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void rejectsNullFacts() {
        assertThatThrownBy(() -> engine.evaluate(null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- helpers -----------------------------------------------------------

    private static TriageFacts facts(Map<String, String> answers, SymptomFact... symptoms) {
        return new TriageFacts(Period.ofYears(45), answers, List.of(symptoms));
    }

    private static SymptomFact symptom(String name, SymptomSeverity severity) {
        return new SymptomFact(name, severity, SymptomOnset.SUDDEN, null);
    }
}
