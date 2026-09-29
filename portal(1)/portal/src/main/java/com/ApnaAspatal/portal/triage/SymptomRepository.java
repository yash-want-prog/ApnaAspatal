package com.ApnaAspatal.portal.triage;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Symptom}. Spring Data generates the implementation at
 * startup.
 */
public interface SymptomRepository extends JpaRepository<Symptom, Long> {
}
