package com.ApnaAspatal.portal.triage.audit;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.ApnaAspatal.portal.triage.TriageSession;

/**
 * One recorded change to a triage session: who changed what, from what, to what,
 * and when.
 *
 * <p>Append-only - there are no setters, and nothing updates or deletes events.
 * Answers themselves are replaced and removed as a patient moves through the
 * adaptive flow; this log is what keeps that history, so a triage decision can
 * be traced back to the answers that produced it.
 */
@Entity
@Table(name = "triage_audit_events")
public class TriageAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "triage_session_id", nullable = false)
    private TriageSession triageSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TriageAuditEventType eventType;

    /** What the event is about: a question key, a symptom name, or a risk level. */
    @Column(length = 120)
    private String subject;

    @Column(length = 1000)
    private String previousValue;

    @Column(length = 1000)
    private String newValue;

    /** The session's inputs version after the event. */
    @Column(nullable = false)
    private long inputsVersion;

    /** The account that caused the event. */
    @Column(nullable = false)
    private Long actorUserId;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    /**
     * Required by JPA. Hibernate instantiates entities reflectively before
     * populating their fields.
     */
    protected TriageAuditEvent() {
    }

    TriageAuditEvent(TriageSession triageSession, TriageAuditEventType eventType, String subject,
            String previousValue, String newValue, long inputsVersion, Long actorUserId,
            LocalDateTime occurredAt) {
        this.triageSession = triageSession;
        this.eventType = eventType;
        this.subject = subject;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.inputsVersion = inputsVersion;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public TriageSession getTriageSession() {
        return triageSession;
    }

    public TriageAuditEventType getEventType() {
        return eventType;
    }

    public String getSubject() {
        return subject;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public long getInputsVersion() {
        return inputsVersion;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
