package com.ApnaAspatal.portal.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Department names are stored by name in triage_results, so they are a storage
 * contract. Renaming or removing one would strand rows already written.
 *
 * <p>If this test fails because a department was deliberately added, update the
 * expected list. If it fails because one was renamed or removed, that needs a
 * data migration first.
 */
class TriageDepartmentTest {

    @Test
    void storedDepartmentNamesAreStable() {
        assertThat(Arrays.stream(TriageDepartment.values()).map(Enum::name))
                .containsExactlyInAnyOrder("EMERGENCY", "GENERAL_MEDICINE");
    }

    @Test
    void storedNameRoundTripsToTheSameDepartment() {
        for (TriageDepartment department : TriageDepartment.values()) {
            assertThat(TriageDepartment.valueOf(department.name())).isSameAs(department);
        }
    }

    @Test
    void everyStoredNameFitsTheColumn() {
        // triage_results.recommended_department is varchar(100).
        for (TriageDepartment department : TriageDepartment.values()) {
            assertThat(department.name()).hasSizeLessThanOrEqualTo(100);
        }
    }
}
