package com.ApnaAspatal.portal.triage.question;

import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Access to the question bank. The public entry point for the question
 * sub-package: callers go through this rather than the repository.
 */
@Service
public class TriageQuestionService {

    private final TriageQuestionRepository triageQuestionRepository;

    public TriageQuestionService(TriageQuestionRepository triageQuestionRepository) {
        this.triageQuestionRepository = triageQuestionRepository;
    }

    /**
     * Resolves a question by id. The single place question-not-found is decided.
     *
     * @throws TriageQuestionNotFoundException if no question exists with the given id
     */
    public TriageQuestion getQuestionById(Long questionId) {
        return triageQuestionRepository.findById(questionId)
                .orElseThrow(() -> new TriageQuestionNotFoundException(questionId));
    }

    /**
     * Every question that may currently be asked, in a stable order.
     */
    public List<TriageQuestion> getActiveQuestions() {
        return triageQuestionRepository.findByActiveTrueOrderByIdAsc();
    }
}
