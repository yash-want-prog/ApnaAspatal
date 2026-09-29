package com.ApnaAspatal.portal.triage;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;

import com.ApnaAspatal.portal.patient.Patient;

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

/**
 * One run of the triage process for a single patient.
 *
 * <p>A patient may be triaged many times, so many sessions point at one patient.
 * The relationship is deliberately unidirectional: a session always needs to
 * know its patient, but nothing yet needs to walk from a patient to every
 * session they have ever had.
 */
@Entity
@Table(name = "triage_sessions")
public class TriageSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TriageSessionStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    /**
     * Incremented whenever anything the rule engine reads changes - an answer
     * recorded, changed or removed, or a symptom added. A result is current only
     * if it was computed at the session's present inputs version.
     *
     * <p>The database default fills the column for sessions that existed before
     * it was added; {@code ddl-auto=update} cannot add a NOT NULL column to a
     * non-empty table without one.
     */
    @ColumnDefault("0")
    @Column(nullable = false)
    private long inputsVersion;

    /**
     * Required by JPA. Hibernate instantiates entities reflectively before
     * populating their fields.
     */
    protected TriageSession() {
    }

    public TriageSession(Patient patient, TriageSessionStatus status, LocalDateTime startedAt) {
        this.patient = patient;
        this.status = status;
        this.startedAt = startedAt;
    }

    public Long getId() {
        return id;
    }

    /**
     * No setter: a session belongs to the patient it was opened for and must not
     * be reassigned.
     */
    public Patient getPatient() {
        return patient;
    }

    public TriageSessionStatus getStatus() {
        return status;
    }

    public void setStatus(TriageSessionStatus status) {
        this.status = status;
    }

    /**
     * No setter: when a session started is a historical fact.
     */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    /**
     * Null while the session is still {@code IN_PROGRESS}.
     */
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public long getInputsVersion() {
        return inputsVersion;
    }

    /**
     * Records that the rule engine's inputs changed, so any existing result is no
     * longer current. Package-private: only the triage services that change
     * answers and symptoms may call it, and only while holding the session lock.
     */
    void recordInputsChanged() {
        inputsVersion++;
    }
}
