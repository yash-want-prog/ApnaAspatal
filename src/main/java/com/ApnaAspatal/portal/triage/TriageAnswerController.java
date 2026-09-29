package com.ApnaAspatal.portal.triage;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ApnaAspatal.portal.triage.dto.NextQuestionResponse;
import com.ApnaAspatal.portal.triage.dto.TriageAnswerRequest;
import com.ApnaAspatal.portal.triage.dto.TriageAnswerResponse;

/**
 * HTTP entry point for answering triage questions, nested under the session.
 */
@RestController
@RequestMapping("/api/triage-sessions/{sessionId}")
public class TriageAnswerController {

    private final TriageAnswerService triageAnswerService;

    public TriageAnswerController(TriageAnswerService triageAnswerService) {
        this.triageAnswerService = triageAnswerService;
    }

    /**
     * 201 when the question is answered for the first time; 200 when an existing
     * answer is replaced - nothing new is created then. The body is the same
     * either way.
     */
    @PostMapping("/answers")
    public ResponseEntity<TriageAnswerResponse> submit(@PathVariable Long sessionId,
            @Valid @RequestBody TriageAnswerRequest request) {
        TriageAnswerService.SubmittedAnswer submitted = triageAnswerService.submitAnswer(sessionId, request);
        return ResponseEntity
                .status(submitted.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(submitted.answer());
    }

    /**
     * 200 with the next question, or 204 when the session has answered every
     * active question.
     */
    @GetMapping("/next-question")
    public ResponseEntity<NextQuestionResponse> nextQuestion(@PathVariable Long sessionId) {
        return triageAnswerService.findNextQuestion(sessionId)
                .map(question -> ResponseEntity.ok(new NextQuestionResponse(
                        question.getId(),
                        question.getQuestionKey(),
                        question.getQuestionText(),
                        question.getAnswerType())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
