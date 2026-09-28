package com.ApnaAspatal.portal.triage;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link TriageResult}. Spring Data generates the
 * implementation at startup.
 */
public interface TriageResultRepository extends JpaRepository<TriageResult, Long> {

    /**
     * The result for one session, if it has been evaluated. At most one can
     * exist - the foreign key column is unique.
     */
    Optional<TriageResult> findByTriageSessionId(Long triageSessionId);
}
