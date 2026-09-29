package com.ApnaAspatal.portal.triage;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link TriageSession}. Spring Data generates the
 * implementation at startup.
 */
public interface TriageSessionRepository extends JpaRepository<TriageSession, Long> {
}
