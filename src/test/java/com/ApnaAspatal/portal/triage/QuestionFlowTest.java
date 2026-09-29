package com.ApnaAspatal.portal.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ApnaAspatal.portal.patient.Patient;
import com.ApnaAspatal.portal.triage.question.QuestionAnswerType;
import com.ApnaAspatal.portal.triage.question.TriageQuestion;

/**
 * Reachability through the adaptive question bank - the rules every part of the
 * question flow shares.
 */
class QuestionFlowTest {

    private final TriageSession session = new TriageSession(
            new Patient("Asha Patel", "asha@example.com", "+919876543210", LocalDate.of(1991, 4, 12)),
            TriageSessionStatus.IN_PROGRESS, LocalDateTime.of(2026, 9, 29, 10, 0));

    private final TriageQuestion chestPain = question("CHEST_PAIN", null, null);
    private final TriageQuestion fever = question("FEVER", null, null);
    private final TriageQuestion breathing = question("BREATHING_DIFFICULTY", "CHEST_PAIN", "YES");
    private final TriageQuestion temperature = question("TEMPERATURE", "FEVER", "YES");

    private final List<TriageQuestion> bank = List.of(chestPain, fever, breathing, temperature);

    // --- next question -----------------------------------------------------

    @Test
    void nextQuestionIsTheFirstUnlockedUnansweredOne() {
        assertThat(flow().nextQuestion()).contains(chestPain);
        assertThat(flow(answer(chestPain, "YES")).nextQuestion()).contains(fever);
        assertThat(flow(answer(chestPain, "YES"), answer(fever, "NO")).nextQuestion()).contains(breathing);
    }

    @Test
    void pathEndsWhenEveryUnlockedQuestionIsAnswered() {
        assertThat(flow(answer(chestPain, "NO"), answer(fever, "NO")).nextQuestion()).isEmpty();
    }

    // --- unlocking ---------------------------------------------------------

    @Test
    void rootQuestionIsAlwaysUnlocked() {
        assertThat(flow().isUnlocked(chestPain)).isTrue();
    }

    @Test
    void followUpUnlocksOnlyOnTheRequiredAnswerRegardlessOfCase() {
        assertThat(flow().isUnlocked(breathing)).isFalse();
        assertThat(flow(answer(chestPain, "NO")).isUnlocked(breathing)).isFalse();
        assertThat(flow(answer(chestPain, "yes")).isUnlocked(breathing)).isTrue();
    }

    @Test
    void followUpWithNoRequiredAnswerUnlocksOnAnyAnswer() {
        TriageQuestion anyAnswer = question("DETAIL", "CHEST_PAIN", null);

        assertThat(flow().isUnlocked(anyAnswer)).isFalse();
        assertThat(flow(answer(chestPain, "NO")).isUnlocked(anyAnswer)).isTrue();
    }

    // --- unreachable answers -----------------------------------------------

    @Test
    void consistentPathHasNoUnreachableAnswers() {
        assertThat(flow(answer(chestPain, "YES"), answer(fever, "NO"), answer(breathing, "YES"))
                .unreachableAnswers()).isEmpty();
    }

    @Test
    void closingABranchMakesItsAnswersUnreachableAndKeepsUnrelatedOnes() {
        TriageAnswer changedParent = answer(chestPain, "NO");
        TriageAnswer unrelated = answer(fever, "NO");
        TriageAnswer staleChild = answer(breathing, "YES");

        assertThat(flow(changedParent, unrelated, staleChild).unreachableAnswers()).containsExactly(staleChild);
    }

    @Test
    void unreachabilityCascadesThroughAChain() {
        // CHEST_PAIN -> BREATHING_DIFFICULTY -> OXYGEN. Closing the first branch
        // strands the grandchild too, even though its own parent's answer was YES.
        TriageQuestion oxygen = question("OXYGEN", "BREATHING_DIFFICULTY", "YES");
        TriageAnswer staleChild = answer(breathing, "YES");
        TriageAnswer staleGrandchild = answer(oxygen, "YES");

        // Grandchild listed first: the result must not depend on examination order.
        QuestionFlow flow = new QuestionFlow(List.of(chestPain, breathing, oxygen),
                List.of(staleGrandchild, answer(chestPain, "NO"), staleChild));

        assertThat(flow.unreachableAnswers()).containsExactlyInAnyOrder(staleChild, staleGrandchild);
    }

    @Test
    void answerToADeactivatedQuestionIsNotTreatedAsUnreachable() {
        // DURATION is answered but no longer in the active bank; retiring a question
        // must not rewrite the history of sessions that already answered it.
        TriageQuestion retired = question("DURATION", null, null);
        TriageAnswer historical = answer(retired, "2 days");

        assertThat(flow(answer(chestPain, "NO"), historical).unreachableAnswers()).isEmpty();
    }

    // --- helpers -----------------------------------------------------------

    private QuestionFlow flow(TriageAnswer... answers) {
        return new QuestionFlow(bank, List.of(answers));
    }

    private TriageAnswer answer(TriageQuestion question, String value) {
        return new TriageAnswer(session, question, value, LocalDateTime.of(2026, 9, 29, 10, 5));
    }

    private static TriageQuestion question(String key, String dependsOnKey, String dependsOnAnswer) {
        return new TriageQuestion(key, key + "?", QuestionAnswerType.BOOLEAN, dependsOnKey, dependsOnAnswer);
    }
}
