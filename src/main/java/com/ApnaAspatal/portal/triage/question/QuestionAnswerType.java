package com.ApnaAspatal.portal.triage.question;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The kind of answer a triage question expects, and what makes an answer valid.
 *
 * <p>Answers are checked when they are submitted, so a malformed one is rejected
 * while the patient can still correct it - rather than being stored and later
 * treated as missing information when the session is evaluated.
 *
 * <p>Persisted by name in {@code triage_questions.answer_type}. The names are a
 * storage contract: add types freely, but renaming one is a data migration.
 */
public enum QuestionAnswerType {

    /** A yes/no question. Accepted in any case; stored as {@code YES} or {@code NO}. */
    BOOLEAN("YES or NO") {
        @Override
        public Optional<String> normalise(String answer) {
            String trimmed = answer.strip();
            if (trimmed.equalsIgnoreCase(YES)) {
                return Optional.of(YES);
            }
            if (trimmed.equalsIgnoreCase(NO)) {
                return Optional.of(NO);
            }
            return Optional.empty();
        }
    },

    /**
     * A plain decimal number such as {@code 38.5}. Units, exponents, commas, and
     * values like {@code NaN} are rejected. Stored without surrounding space.
     */
    NUMBER("a plain number such as 38.5") {
        @Override
        public Optional<String> normalise(String answer) {
            String trimmed = answer.strip();
            return PLAIN_DECIMAL.matcher(trimmed).matches() ? Optional.of(trimmed) : Optional.empty();
        }
    },

    /** Free text. Any non-blank answer is valid and stored exactly as given. */
    TEXT("non-blank text") {
        @Override
        public Optional<String> normalise(String answer) {
            return answer.isBlank() ? Optional.empty() : Optional.of(answer);
        }
    };

    private static final String YES = "YES";
    private static final String NO = "NO";

    /** Optional minus sign, digits, and an optional fractional part. */
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    private final String expectedFormat;

    QuestionAnswerType(String expectedFormat) {
        this.expectedFormat = expectedFormat;
    }

    /**
     * The answer in the form it should be stored, or empty if it is not a valid
     * answer of this type.
     *
     * @param answer the answer as submitted; never {@code null}
     */
    public abstract Optional<String> normalise(String answer);

    /**
     * A short description of a valid answer, for error messages.
     */
    public String expectedFormat() {
        return expectedFormat;
    }
}
