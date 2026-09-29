package com.ApnaAspatal.portal.health;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness endpoint for SmartTriage.
 *
 * <p>Answers a single question: is this application process running and able to
 * serve HTTP? It deliberately does not check the database or any other
 * dependency - that is a readiness concern, and Spring Boot Actuator handles it
 * properly when we are ready for it.
 *
 * <p>Public: it reveals nothing about any patient.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", "smarttriage", Instant.now(clock));
    }

    /**
     * Response body for {@link #health()}. Jackson turns each record component
     * into a JSON field of the same name.
     */
    public record HealthResponse(String status, String service, Instant timestamp) {
    }
}
