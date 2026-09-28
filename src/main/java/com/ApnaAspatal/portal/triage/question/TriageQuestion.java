package com.ApnaAspatal.portal.triage.question;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One question SmartTriage can ask during a triage session.
 *
 * <p>This is reference data, not session data: a question exists independently
 * of any patient or session, and the same question is reused across every
 * session that asks it.
 */
@Entity
@Table(name = "triage_questions")
public class TriageQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stable identifier used by rules and code, for example {@code CHEST_PAIN}.
     * Unique, and never changes once a question is in use - stored answers refer
     * to questions by this key rather than by wording.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String questionKey;

    @Column(nullable = false, length = 500)
    private String questionText;

    @Column(nullable = false, length = 32)
    private String answerType;

    /**
     * Whether the question may still be asked. Questions are deactivated rather
     * than deleted, so answers recorded against them remain interpretable.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * The {@code questionKey} of the question this one follows on from, or null
     * for a root question that can always be asked.
     *
     * <p>A key rather than a foreign key: dependencies are authored as part of
     * the question bank, and keeping them as keys keeps the rule readable and
     * lets questions be seeded in any order.
     */
    @Column(length = 64)
    private String dependsOnQuestionKey;

    /**
     * The answer the prerequisite question must have been given for this question
     * to become eligible. Null means any answer will do.
     */
    @Column(length = 100)
    private String dependsOnAnswer;

    /**
     * Required by JPA. Hibernate instantiates entities reflectively before
     * populating their fields.
     */
    protected TriageQuestion() {
    }

    public TriageQuestion(String questionKey, String questionText, String answerType) {
        this(questionKey, questionText, answerType, null, null);
    }

    public TriageQuestion(String questionKey, String questionText, String answerType,
            String dependsOnQuestionKey, String dependsOnAnswer) {
        this.questionKey = questionKey;
        this.questionText = questionText;
        this.answerType = answerType;
        this.dependsOnQuestionKey = dependsOnQuestionKey;
        this.dependsOnAnswer = dependsOnAnswer;
    }

    public Long getId() {
        return id;
    }

    /**
     * No setter: the key is the stable handle other data refers to. Changing it
     * would orphan every answer recorded against this question.
     */
    public String getQuestionKey() {
        return questionKey;
    }

    public String getQuestionText() {
        return questionText;
    }

    /**
     * Wording may be corrected or improved without changing what the question
     * means, so this is mutable while the key is not.
     */
    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    /**
     * No setter: changing the answer type of a question already in use would
     * invalidate answers already stored against it.
     */
    public String getAnswerType() {
        return answerType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Null for a root question.
     */
    public String getDependsOnQuestionKey() {
        return dependsOnQuestionKey;
    }

    /**
     * Null when any answer to the prerequisite makes this question eligible.
     */
    public String getDependsOnAnswer() {
        return dependsOnAnswer;
    }
}
