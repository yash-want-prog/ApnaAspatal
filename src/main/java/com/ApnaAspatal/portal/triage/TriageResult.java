package com.ApnaAspatal.portal.triage;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * The outcome of evaluating one triage session.
 *
 * <p>One session has at most one result: re-evaluating a session updates this
 * row rather than adding another.
 *
 * <p>Nothing here is decided by this class - it only records what the rule
 * engine concluded, and why.
 */
@Entity
@Table(name = "triage_results")
public class TriageResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "triage_session_id", nullable = false, unique = true)
    private TriageSession triageSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TriageRiskLevel riskLevel;

    @Column(nullable = false, length = 100)
    private String recommendedDepartment;

    /**
     * Why the engine reached this conclusion, as a comma-separated list of rule
     * codes such as {@code CHEST_PAIN_YES,BREATHING_DIFFICULTY_YES}.
     *
     * <p>This is what makes a triage decision auditable: a risk level with no
     * stated reason cannot be reviewed by a clinician.
     */
    @Column(nullable = false, length = 500)
    private String reasonCodes;

    @Column(nullable = false)
    private LocalDateTime evaluatedAt;

    /**
     * The session's inputs version this result was computed from. The result is
     * current only while the session is still at that version; once an answer or
     * symptom changes, it is stale until the session is evaluated again.
     *
     * <p>Null for a result not stamped with a version - one stored before versions
     * existed, or one produced outside {@code TriageResultService}. Such a result
     * is never treated as current, so staleness fails safe.
     */
    private Long evaluatedInputsVersion;

    /**
     * Required by JPA. Hibernate instantiates entities reflectively before
     * populating their fields.
     */
    protected TriageResult() {
    }

    public TriageResult(TriageSession triageSession, TriageRiskLevel riskLevel,
            String recommendedDepartment, String reasonCodes, LocalDateTime evaluatedAt) {
        this.triageSession = triageSession;
        this.riskLevel = riskLevel;
        this.recommendedDepartment = recommendedDepartment;
        this.reasonCodes = reasonCodes;
        this.evaluatedAt = evaluatedAt;
    }

    public Long getId() {
        return id;
    }

    /**
     * No setter: a result belongs to the session it was computed for.
     */
    public TriageSession getTriageSession() {
        return triageSession;
    }

    public TriageRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(TriageRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getRecommendedDepartment() {
        return recommendedDepartment;
    }

    public void setRecommendedDepartment(String recommendedDepartment) {
        this.recommendedDepartment = recommendedDepartment;
    }

    public String getReasonCodes() {
        return reasonCodes;
    }

    public void setReasonCodes(String reasonCodes) {
        this.reasonCodes = reasonCodes;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(LocalDateTime evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public Long getEvaluatedInputsVersion() {
        return evaluatedInputsVersion;
    }

    /**
     * Package-private: only {@code TriageResultService} stamps a result, while it
     * holds the session lock, so the version recorded is the one evaluated.
     */
    void recordEvaluatedInputsVersion(long inputsVersion) {
        this.evaluatedInputsVersion = inputsVersion;
    }

    /**
     * Whether this result still describes the session's current answers and
     * symptoms.
     */
    public boolean isCurrentFor(TriageSession session) {
        return evaluatedInputsVersion != null && evaluatedInputsVersion == session.getInputsVersion();
    }
}
