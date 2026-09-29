package com.ApnaAspatal.portal.triage.engine;

import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.BREATHING_DIFFICULTY;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.CHEST_PAIN;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.ELEVATED_TEMPERATURE;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.FEVER;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.INSUFFICIENT_INFORMATION;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.NO_HIGH_RISK_RULE_MATCHED;
import static com.ApnaAspatal.portal.triage.engine.TriageReasonCodes.SEVERE_SYMPTOM;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.ApnaAspatal.portal.triage.SymptomSeverity;
import com.ApnaAspatal.portal.triage.TriageDepartment;
import com.ApnaAspatal.portal.triage.TriageRiskLevel;
import com.ApnaAspatal.portal.triage.engine.TriageFacts.SymptomFact;

/**
 * The first deterministic triage rules, for the MVP/demo care-navigation flow.
 *
 * <p><b>This is not a diagnostic system.</b> It assigns an urgency level and a
 * destination from a handful of red-flag answers. The thresholds here are
 * placeholders chosen for demonstration and have not been clinically validated.
 *
 * <p>Rules are checked in priority order and the first match wins:
 * <ol>
 *   <li>HIGH, rule A - chest pain with breathing difficulty</li>
 *   <li>HIGH, rule B - chest pain reported as a severe symptom</li>
 *   <li>MODERATE, rule C - fever with an elevated temperature</li>
 *   <li>INSUFFICIENT INFORMATION, rule E - a higher-risk rule could not be ruled out</li>
 *   <li>LOW, rule D - every rule above was checked and ruled out</li>
 * </ol>
 *
 * <p>Rule E is deliberately checked before rule D. "No rule matched" only means
 * low risk if every rule could actually be evaluated; when information is
 * missing, no rule can match, and treating that as LOW would under-triage.
 *
 * <p>Stateless and thread-safe: one instance serves every evaluation.
 */
public final class MvpTriageRuleEngine implements TriageRuleEngine {

    // Question keys, as stored in the question bank.
    private static final String CHEST_PAIN_QUESTION = "CHEST_PAIN";
    private static final String BREATHING_DIFFICULTY_QUESTION = "BREATHING_DIFFICULTY";
    private static final String FEVER_QUESTION = "FEVER";
    private static final String TEMPERATURE_QUESTION = "TEMPERATURE";

    private static final String YES = "YES";
    private static final String NO = "NO";

    /** Symptom name that identifies chest pain, after normalisation. */
    private static final String CHEST_PAIN_SYMPTOM = "chest pain";

    /**
     * MVP/demo threshold, in degrees Celsius, at or above which a temperature is
     * treated as elevated. Not clinical guidance.
     */
    static final double ELEVATED_TEMPERATURE_CELSIUS = 38.0;

    /**
     * Input sanity bounds for a temperature read as Celsius. Not clinical
     * thresholds: values outside them are treated as unreadable. They exist
     * because the question does not state a unit, and every Fahrenheit reading
     * (for example 98.6) would otherwise be misread as an elevated Celsius one.
     */
    static final double MIN_PLAUSIBLE_CELSIUS = 30.0;
    static final double MAX_PLAUSIBLE_CELSIUS = 45.0;

    @Override
    public TriageDecision evaluate(TriageFacts facts) {
        Objects.requireNonNull(facts, "facts must not be null");

        Optional<Boolean> chestPain = yesOrNo(facts, CHEST_PAIN_QUESTION);
        Optional<Boolean> breathingDifficulty = yesOrNo(facts, BREATHING_DIFFICULTY_QUESTION);
        Optional<Boolean> fever = yesOrNo(facts, FEVER_QUESTION);
        Optional<Double> temperature = temperatureCelsius(facts);
        List<SymptomSeverity> chestPainSeverities = chestPainSeverities(facts);

        boolean hasChestPain = chestPain.orElse(false);

        // 1. HIGH - rule A.
        if (hasChestPain && breathingDifficulty.orElse(false)) {
            return decision(TriageRiskLevel.HIGH, TriageDepartment.EMERGENCY,
                    CHEST_PAIN, BREATHING_DIFFICULTY);
        }

        // 1. HIGH - rule B.
        if (hasChestPain && chestPainSeverities.contains(SymptomSeverity.SEVERE)) {
            return decision(TriageRiskLevel.HIGH, TriageDepartment.EMERGENCY,
                    CHEST_PAIN, SEVERE_SYMPTOM);
        }

        // 2. MODERATE - rule C.
        if (fever.orElse(false)
                && temperature.map(celsius -> celsius >= ELEVATED_TEMPERATURE_CELSIUS).orElse(false)) {
            return decision(TriageRiskLevel.MODERATE, TriageDepartment.GENERAL_MEDICINE,
                    FEVER, ELEVATED_TEMPERATURE);
        }

        // 3. INSUFFICIENT INFORMATION - rule E, before LOW on purpose.
        if (!chestPainRulesRuledOut(chestPain, breathingDifficulty, chestPainSeverities)
                || !feverRuleRuledOut(fever, temperature)) {
            return decision(TriageRiskLevel.MODERATE, TriageDepartment.GENERAL_MEDICINE,
                    INSUFFICIENT_INFORMATION);
        }

        // 4. LOW - rule D: every rule above was evaluated and none matched.
        return decision(TriageRiskLevel.LOW, TriageDepartment.GENERAL_MEDICINE,
                NO_HIGH_RISK_RULE_MATCHED);
    }

    /**
     * Whether rules A and B were evaluated and genuinely did not apply.
     *
     * <ul>
     *   <li>Chest pain not answered, or not a clear yes/no: cannot rule out.</li>
     *   <li>Answered NO: ruled out - unless a chest-pain symptom was recorded
     *       anyway. That contradiction cannot be resolved safely by picking one.</li>
     *   <li>Answered YES: ruled out only when both follow-ups are known - the
     *       breathing answer (rule A) and the chest-pain severity (rule B).</li>
     * </ul>
     */
    private static boolean chestPainRulesRuledOut(Optional<Boolean> chestPain,
            Optional<Boolean> breathingDifficulty, List<SymptomSeverity> chestPainSeverities) {
        if (chestPain.isEmpty()) {
            return false;
        }
        if (!chestPain.get()) {
            return chestPainSeverities.isEmpty();
        }
        return breathingDifficulty.isPresent() && !chestPainSeverities.isEmpty();
    }

    /**
     * Whether rule C was evaluated and genuinely did not apply: fever answered
     * NO, or answered YES with a readable temperature.
     */
    private static boolean feverRuleRuledOut(Optional<Boolean> fever, Optional<Double> temperature) {
        if (fever.isEmpty()) {
            return false;
        }
        return !fever.get() || temperature.isPresent();
    }

    /**
     * A yes/no answer, or empty when the question was not answered or the answer
     * is neither YES nor NO. An unrecognised answer such as "maybe" is treated as
     * missing, never as NO.
     */
    private static Optional<Boolean> yesOrNo(TriageFacts facts, String questionKey) {
        if (facts.answerIs(questionKey, YES)) {
            return Optional.of(true);
        }
        if (facts.answerIs(questionKey, NO)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    /**
     * The reported temperature in Celsius, or empty when it was not given, is not
     * a plain number, or falls outside the plausible Celsius range.
     */
    private static Optional<Double> temperatureCelsius(TriageFacts facts) {
        return facts.answerFor(TEMPERATURE_QUESTION).flatMap(MvpTriageRuleEngine::parseCelsius);
    }

    private static Optional<Double> parseCelsius(String answer) {
        double celsius;
        try {
            celsius = Double.parseDouble(answer.strip());
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
        // NaN fails both comparisons, so it is rejected here as well.
        boolean plausible = celsius >= MIN_PLAUSIBLE_CELSIUS && celsius <= MAX_PLAUSIBLE_CELSIUS;
        return plausible ? Optional.of(celsius) : Optional.empty();
    }

    /**
     * Severities of every recorded symptom that is chest pain. Empty when chest
     * pain was not recorded as a symptom, meaning its severity is unknown.
     */
    private static List<SymptomSeverity> chestPainSeverities(TriageFacts facts) {
        return facts.symptoms().stream()
                .filter(symptom -> normalise(symptom.name()).equals(CHEST_PAIN_SYMPTOM))
                .map(SymptomFact::severity)
                .toList();
    }

    /**
     * Lower-cases with {@link Locale#ROOT} so the result does not depend on the
     * machine's locale - in a Turkish locale, "I" lower-cases to a dotless
     * character and "CHEST PAIN" would stop matching.
     */
    private static String normalise(String symptomName) {
        return symptomName.strip()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
    }

    private static TriageDecision decision(TriageRiskLevel riskLevel, TriageDepartment department,
            String... reasonCodes) {
        return new TriageDecision(riskLevel, department, List.of(reasonCodes));
    }
}
