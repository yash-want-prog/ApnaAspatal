package com.ApnaAspatal.portal.triage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link TriageAnswer}. Spring Data generates the
 * implementation at startup.
 */
public interface TriageAnswerRepository extends JpaRepository<TriageAnswer, Long> {

    /**
     * Every answer given in one session, with its question already loaded.
     *
     * <p>The {@code join fetch} matters: the engine reads each answer's question
     * key, which is a lazy field. Without it, reading N answers would issue N
     * extra queries to initialise each question proxy.
     */
    @Query("select a from TriageAnswer a join fetch a.question where a.triageSession.id = :sessionId")
    List<TriageAnswer> findBySessionIdWithQuestion(@Param("sessionId") Long sessionId);
}
