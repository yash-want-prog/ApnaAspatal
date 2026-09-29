package com.ApnaAspatal.portal.triage.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link TriageAuditEvent}. Events are only ever inserted.
 */
public interface TriageAuditEventRepository extends JpaRepository<TriageAuditEvent, Long> {
}
