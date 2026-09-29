package com.ApnaAspatal.portal.triage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ApnaAspatal.portal.triage.question.TriageQuestion;

/**
 * The adaptive path through the question bank for one session, given the answers
 * recorded so far.
 *
 * <p>The single place that decides reachability: which question is asked next,
 * which questions may be answered, and which recorded answers have fallen off
 * the path because an earlier answer changed. Pure logic - it loads and saves
 * nothing, so every caller sees exactly the same rules.
 *
 * <p>A question is <em>unlocked</em> when it has no prerequisite, or its
 * prerequisite has been answered with the required value.
 */
final class QuestionFlow {

    private final List<TriageQuestion> activeQuestions;
    private final List<TriageAnswer> answers;
    private final Map<String, String> answersByQuestionKey;

    /**
     * @param activeQuestions the questions that may be asked, in asking order
     * @param answers         every answer currently recorded for the session, with
     *                        its question loaded
     */
    QuestionFlow(List<TriageQuestion> activeQuestions, List<TriageAnswer> answers) {
        this.activeQuestions = List.copyOf(activeQuestions);
        this.answers = List.copyOf(answers);
        this.answersByQuestionKey = valuesByQuestionKey(this.answers);
    }

    /**
     * The first active question that is unlocked and not yet answered, or empty
     * when the session has reached the end of its path.
     */
    Optional<TriageQuestion> nextQuestion() {
        return activeQuestions.stream()
                .filter(question -> !isAnswered(question))
                .filter(this::isUnlocked)
                .findFirst();
    }

    boolean isAnswered(TriageQuestion question) {
        return answersByQuestionKey.containsKey(question.getQuestionKey());
    }

    boolean isUnlocked(TriageQuestion question) {
        return isUnlocked(question, answersByQuestionKey);
    }

    /**
     * Recorded answers that are no longer on the session's path.
     *
     * <p>An answer is unreachable when its question is locked by the other
     * answers. Removing one can lock further questions - a grandchild whose
     * parent's answer has just gone - so removal repeats until nothing more
     * changes. Removal is monotonic, so the result does not depend on the order
     * answers are examined in.
     *
     * <p>Only dependencies are considered. An answer to a question that has since
     * been deactivated is left alone: deactivation retires a question for future
     * sessions and must not rewrite history.
     */
    List<TriageAnswer> unreachableAnswers() {
        Map<String, String> reachable = new HashMap<>(answersByQuestionKey);

        boolean removedAny;
        do {
            removedAny = false;
            for (TriageAnswer answer : answers) {
                TriageQuestion question = answer.getQuestion();
                if (reachable.containsKey(question.getQuestionKey()) && !isUnlocked(question, reachable)) {
                    reachable.remove(question.getQuestionKey());
                    removedAny = true;
                }
            }
        } while (removedAny);

        return answers.stream()
                .filter(answer -> !reachable.containsKey(answer.getQuestion().getQuestionKey()))
                .toList();
    }

    private static boolean isUnlocked(TriageQuestion question, Map<String, String> answersByQuestionKey) {
        String dependsOnQuestionKey = question.getDependsOnQuestionKey();
        if (dependsOnQuestionKey == null || dependsOnQuestionKey.isBlank()) {
            return true;
        }

        String givenAnswer = answersByQuestionKey.get(dependsOnQuestionKey);
        if (givenAnswer == null) {
            return false;
        }

        String requiredAnswer = question.getDependsOnAnswer();
        if (requiredAnswer == null || requiredAnswer.isBlank()) {
            return true;
        }

        return requiredAnswer.equalsIgnoreCase(givenAnswer);
    }

    /**
     * At most one answer per question per session (unique constraint), so keys
     * cannot collide.
     */
    private static Map<String, String> valuesByQuestionKey(List<TriageAnswer> answers) {
        return answers.stream()
                .collect(Collectors.toMap(
                        answer -> answer.getQuestion().getQuestionKey(),
                        TriageAnswer::getAnswerValue));
    }
}
