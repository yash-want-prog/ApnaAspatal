package com.ApnaAspatal.portal.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's single source of "now".
 *
 * <p>Services inject this {@link Clock} rather than calling the system clock
 * directly, so time can be fixed in tests and every time-dependent calculation
 * uses the same, explicit timezone.
 */
@Configuration
public class ClockConfig {

    /**
     * The zone is explicit rather than the JVM default, because calendar
     * calculations depend on it: a patient's birthday is a local date, and
     * whether they have turned 65 "today" depends on whose today it is.
     *
     * <p>No default value: a missing property fails at startup, and so does an
     * invalid zone id, rather than silently falling back to the server's zone.
     */
    @Bean
    public Clock clock(@Value("${smarttriage.time-zone}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
