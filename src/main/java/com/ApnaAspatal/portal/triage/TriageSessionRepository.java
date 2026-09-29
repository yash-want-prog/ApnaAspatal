package com.ApnaAspatal.portal.triage;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link TriageSession}. Spring Data generates the
 * implementation at startup.
 *
 * <p>Application code reads sessions only through the owner-scoped queries
 * below: a session is reachable only by the account that owns its patient.
 */
public interface TriageSessionRepository extends JpaRepository<TriageSession, Long> {

    @Query("select s from TriageSession s where s.id = :sessionId and s.patient.owner.id = :ownerId")
    Optional<TriageSession> findOwned(@Param("sessionId") Long sessionId, @Param("ownerId") Long ownerId);

    /**
     * The same session, locked ({@code SELECT ... FOR UPDATE}) until the
     * surrounding transaction ends. Operations that change a session's inputs or
     * evaluate it take this lock, so they run one at a time per session. Must be
     * called inside a read-write transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TriageSession s where s.id = :sessionId and s.patient.owner.id = :ownerId")
    Optional<TriageSession> findOwnedForUpdate(@Param("sessionId") Long sessionId, @Param("ownerId") Long ownerId);
}
