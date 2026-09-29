package com.ApnaAspatal.portal.triage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Symptom}. Spring Data generates the implementation at
 * startup.
 */
public interface SymptomRepository extends JpaRepository<Symptom, Long> {

    /**
     * Every symptom recorded in one session, in the order they were recorded.
     * The explicit order keeps evaluation deterministic: the same session always
     * produces the same list.
     */
    List<Symptom> findByTriageSessionIdOrderByIdAsc(Long triageSessionId);
}
