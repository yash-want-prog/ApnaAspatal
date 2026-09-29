package com.ApnaAspatal.portal.triage.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ApnaAspatal.portal.triage.TriageDepartment;
import com.ApnaAspatal.portal.triage.TriageRiskLevel;

/**
 * A decision must be complete and tamper-proof: an incomplete one cannot be
 * persisted as a TriageResult or reviewed by a clinician, and a mutable one
 * could change after it was made.
 */
class TriageDecisionTest {

    @Test
    void keepsMultipleReasonCodesInTheOrderTheyFired() {
        TriageDecision decision = new TriageDecision(
                TriageRiskLevel.HIGH,
                TriageDepartment.EMERGENCY,
                List.of("CHEST_PAIN_YES", "BREATHING_DIFFICULTY_YES"));

        assertThat(decision.riskLevel()).isEqualTo(TriageRiskLevel.HIGH);
        assertThat(decision.recommendedDepartment()).isEqualTo(TriageDepartment.EMERGENCY);
        assertThat(decision.reasonCodes()).containsExactly("CHEST_PAIN_YES", "BREATHING_DIFFICULTY_YES");
    }

    @Test
    void reasonCodesCannotBeModifiedThroughTheAccessor() {
        TriageDecision decision = decisionWith(List.of("NO_RED_FLAGS"));

        assertThatThrownBy(() -> decision.reasonCodes().add("INJECTED"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void laterChangesToTheCallersListDoNotLeakIn() {
        List<String> codes = new ArrayList<>(List.of("NO_RED_FLAGS"));

        TriageDecision decision = decisionWith(codes);
        codes.add("ADDED_AFTERWARDS");

        assertThat(decision.reasonCodes()).containsExactly("NO_RED_FLAGS");
    }

    @Test
    void requiresAtLeastOneReasonCode() {
        assertThatThrownBy(() -> decisionWith(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void rejectsBlankReasonCode() {
        assertThatThrownBy(() -> decisionWith(List.of("CHEST_PAIN_YES", "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsNullReasonCode() {
        assertThatThrownBy(() -> decisionWith(Arrays.asList("CHEST_PAIN_YES", null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsReasonCodeContainingTheStorageSeparator() {
        // Stored comma-separated, this would read back as two codes.
        String twoCodesInOne = "FEVER" + TriageDecision.REASON_CODE_SEPARATOR + "HIGH";

        assertThatThrownBy(() -> decisionWith(List.of(twoCodesInOne)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(twoCodesInOne);
    }

    @Test
    void rejectsNullRiskLevel() {
        assertThatThrownBy(() -> new TriageDecision(null, TriageDepartment.EMERGENCY, List.of("CHEST_PAIN_YES")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("riskLevel");
    }

    @Test
    void rejectsNullDepartment() {
        assertThatThrownBy(() -> new TriageDecision(TriageRiskLevel.LOW, null, List.of("NO_RED_FLAGS")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("recommendedDepartment");
    }

    private static TriageDecision decisionWith(List<String> reasonCodes) {
        return new TriageDecision(TriageRiskLevel.LOW, TriageDepartment.GENERAL_MEDICINE, reasonCodes);
    }
}
