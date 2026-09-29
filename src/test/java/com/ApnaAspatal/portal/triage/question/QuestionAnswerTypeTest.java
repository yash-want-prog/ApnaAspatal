package com.ApnaAspatal.portal.triage.question;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Each answer type decides what a valid answer looks like and how it is stored.
 * Rejecting a malformed answer here lets the patient correct it immediately,
 * instead of the session later being evaluated as having insufficient
 * information.
 */
class QuestionAnswerTypeTest {

    // --- BOOLEAN -----------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "YES,     YES",
            "yes,     YES",
            "' No ',  NO",
            "nO,      NO"})
    void booleanAcceptsYesOrNoAndStoresThemInCanonicalForm(String answer, String stored) {
        assertThat(QuestionAnswerType.BOOLEAN.normalise(answer)).contains(stored);
    }

    @ParameterizedTest
    @ValueSource(strings = {"maybe", "Y", "true", "1", "YES NO", "   "})
    void booleanRejectsAnythingElse(String answer) {
        assertThat(QuestionAnswerType.BOOLEAN.normalise(answer)).isEmpty();
    }

    // --- NUMBER ------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "38.5,      38.5",
            "' 38.5 ',  38.5",
            "37,        37",
            "0,         0",
            "-1.5,      -1.5"})
    void numberAcceptsAPlainDecimal(String answer, String stored) {
        assertThat(QuestionAnswerType.NUMBER.normalise(answer)).contains(stored);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hot", "38,5", "38.5C", "38.", ".5", "+38", "1e2", "NaN", "Infinity", "   "})
    void numberRejectsAnythingThatIsNotAPlainDecimal(String answer) {
        // Java's own number parser would accept "1e2", "NaN" and "Infinity" -
        // none of which is a sensible patient answer.
        assertThat(QuestionAnswerType.NUMBER.normalise(answer)).isEmpty();
    }

    // --- TEXT --------------------------------------------------------------

    @Test
    void textIsStoredExactlyAsGiven() {
        assertThat(QuestionAnswerType.TEXT.normalise("  about 3 days ")).contains("  about 3 days ");
    }

    @Test
    void textRejectsBlank() {
        assertThat(QuestionAnswerType.TEXT.normalise("   ")).isEmpty();
    }

    // --- storage contract --------------------------------------------------

    @Test
    void storedNamesMatchTheExistingQuestionBank() {
        // triage_questions.answer_type already holds exactly these values, which
        // is why switching the field from String to this enum needs no migration.
        assertThat(Arrays.stream(QuestionAnswerType.values()).map(Enum::name))
                .containsExactlyInAnyOrder("BOOLEAN", "NUMBER", "TEXT");
    }

    @Test
    void everyTypeDescribesTheFormatItExpects() {
        for (QuestionAnswerType type : QuestionAnswerType.values()) {
            assertThat(type.expectedFormat()).isNotBlank();
        }
    }
}
