package com.ApnaAspatal.portal.triage.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ApnaAspatal.portal.triage.TriageRiskLevel;

/**
 * A decision must be complete: an incomplete one cannot be persisted as a
 * TriageResult and could not be reviewed by a clinician.
 */
class TriageDecisionTest {

    @Test
    void acceptsCompleteDecision() {
        TriageDecision decision = new TriageDecision(TriageRiskLevel.HIGH, "EMERGENCY", "CHEST_PAIN_YES");

        assertThat(decision.riskLevel()).isEqualTo(TriageRiskLevel.HIGH);
        assertThat(decision.recommendedDepartment()).isEqualTo("EMERGENCY");
        assertThat(decision.reasonCodes()).isEqualTo("CHEST_PAIN_YES");
    }

    @Test
    void rejectsNullRiskLevel() {
        assertThatThrownBy(() -> new TriageDecision(null, "EMERGENCY", "CHEST_PAIN_YES"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("riskLevel");
    }

    @Test
    void rejectsBlankDepartment() {
        assertThatThrownBy(() -> new TriageDecision(TriageRiskLevel.LOW, "  ", "NO_RED_FLAGS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recommendedDepartment");
    }

    @Test
    void rejectsNullReasonCodes() {
        assertThatThrownBy(() -> new TriageDecision(TriageRiskLevel.LOW, "GENERAL_MEDICINE", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasonCodes");
    }
}
