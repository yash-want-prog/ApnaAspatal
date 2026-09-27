package com.ApnaAspatal.portal.triage;

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
 * A single symptom reported during one triage session.
 *
 * <p>Symptoms have no independent existence: they are observations recorded
 * within a session, and are only ever meaningful in that context. The
 * relationship is unidirectional - a symptom knows its session, but
 * {@code TriageSession} holds no collection of symptoms.
 */
@Entity
@Table(name = "symptoms")
public class Symptom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "triage_session_id", nullable = false)
    private TriageSession triageSession;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SymptomSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SymptomOnset onset;

    /**
     * Free text as reported, for example "3 days". Nullable: a patient may not
     * be able to say how long a symptom has lasted.
     */
    @Column(length = 60)
    private String duration;

    /**
     * Required by JPA. Hibernate instantiates entities reflectively before
     * populating their fields.
     */
    protected Symptom() {
    }

    public Symptom(TriageSession triageSession, String name, SymptomSeverity severity,
            SymptomOnset onset, String duration) {
        this.triageSession = triageSession;
        this.name = name;
        this.severity = severity;
        this.onset = onset;
        this.duration = duration;
    }

    public Long getId() {
        return id;
    }

    /**
     * No setter: a symptom belongs to the session it was recorded in and must not
     * be moved to another.
     */
    public TriageSession getTriageSession() {
        return triageSession;
    }

    /**
     * No setter: the name identifies which symptom this row records. Correcting
     * it means removing the row and recording the right one.
     */
    public String getName() {
        return name;
    }

    public SymptomSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(SymptomSeverity severity) {
        this.severity = severity;
    }

    public SymptomOnset getOnset() {
        return onset;
    }

    public void setOnset(SymptomOnset onset) {
        this.onset = onset;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }
}
