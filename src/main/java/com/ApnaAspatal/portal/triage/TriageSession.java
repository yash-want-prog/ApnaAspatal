package com.ApnaAspatal.portal.triage;

import java.time.LocalDateTime;

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
}
