package com.ApnaAspatal.portal.triage;

import java.time.LocalDateTime;

import com.ApnaAspatal.portal.triage.question.TriageQuestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One question answered during one triage session.
 *
 * <p>Joins session data to the question bank: the session and the question both
 * exist independently, and this row records that a particular question was put
 * to a particular session and what came back.
 *
 * <p>A session answers a given question at most once. Revising an answer updates
 * this row rather than adding another.
 */
@Entity
@Table(
        name = "triage_answers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_triage_answer_session_question",
                columnNames = {"triage_session_id", "triage_question_id"}))
public class TriageAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "triage_session_id", nullable = false)
    private TriageSession triageSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "triage_question_id", nullable = false)
    private TriageQuestion question;

    /**
     * The answer as given, stored as text because the shape depends on the
     * question's answer type - "true", "38.5", or free prose.
     */
    @Column(nullable = false, length = 500)
    private String answerValue;

    @Column(nullable = false)
    private LocalDateTime answeredAt;

    /**
     * Required by JPA. Hibernate instantiates entities reflectively before
     * populating their fields.
     */
    protected TriageAnswer() {
    }

    public TriageAnswer(TriageSession triageSession, TriageQuestion question,
            String answerValue, LocalDateTime answeredAt) {
        this.triageSession = triageSession;
        this.question = question;
        this.answerValue = answerValue;
        this.answeredAt = answeredAt;
    }

    public Long getId() {
        return id;
    }

    /**
     * No setter: an answer belongs to the session it was given in.
     */
    public TriageSession getTriageSession() {
        return triageSession;
    }

    /**
     * No setter: an answer answers one question. A different question is a
     * different answer.
     */
    public TriageQuestion getQuestion() {
        return question;
    }

    public String getAnswerValue() {
        return answerValue;
    }

    public void setAnswerValue(String answerValue) {
        this.answerValue = answerValue;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(LocalDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }
}
