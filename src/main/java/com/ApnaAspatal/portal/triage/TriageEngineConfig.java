package com.ApnaAspatal.portal.triage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ApnaAspatal.portal.triage.engine.MvpTriageRuleEngine;
import com.ApnaAspatal.portal.triage.engine.TriageRuleEngine;

/**
 * Registers the rule engine as a Spring bean.
 *
 * <p>The engine itself carries no Spring annotations - the engine package is
 * kept free of framework code, which {@code TriageEngineIsolationTest} enforces.
 * Wiring it here keeps the rules pure while still making them injectable.
 *
 * <p>Exactly one {@link TriageRuleEngine} bean must exist:
 * {@code TriageEvaluationService} needs one, and Spring refuses to start with
 * none or with two.
 */
@Configuration
public class TriageEngineConfig {

    @Bean
    public TriageRuleEngine triageRuleEngine() {
        return new MvpTriageRuleEngine();
    }
}
