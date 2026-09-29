package com.ApnaAspatal.portal.triage.question;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link TriageQuestion}. Spring Data generates the
 * implementation at startup.
 */
public interface TriageQuestionRepository extends JpaRepository<TriageQuestion, Long> {

    /**
     * Active questions in a stable order. Ordering by id makes question selection
     * deterministic - the same session state always yields the same next question.
     */
    List<TriageQuestion> findByActiveTrueOrderByIdAsc();
}
