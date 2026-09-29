package com.ApnaAspatal.portal.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * The configured zone must be used exactly, and a bad zone must stop the
 * application from starting rather than silently falling back.
 */
class ClockConfigTest {

    @Test
    void usesTheConfiguredZone() {
        assertThat(new ClockConfig().clock("Asia/Kolkata").getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void rejectsAnInvalidZone() {
        assertThatThrownBy(() -> new ClockConfig().clock("Asia/Kolkatta"))
                .isInstanceOf(DateTimeException.class);
    }
}
