package com.ApnaAspatal.portal.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * The configuration that ships in the application jar must be safe to run in
 * production as-is: no SQL logging, and no secrets - only references to the
 * environment. Development conveniences live in the "dev" profile.
 */
class ProductionConfigurationTest {

    private final Properties shipped = load("application.properties");
    private final Properties dev = load("application-dev.properties");

    @Test
    void sqlLoggingIsOffByDefault() {
        assertThat(shipped.getProperty("spring.jpa.show-sql")).isEqualTo("false");
        assertThat(shipped.getProperty("spring.jpa.properties.hibernate.format_sql")).isNull();
    }

    @Test
    void devProfileTurnsSqlLoggingOn() {
        assertThat(dev.getProperty("spring.jpa.show-sql")).isEqualTo("true");
    }

    @Test
    void secretsComeOnlyFromTheEnvironmentWithNoFallback() {
        // A default after a colon (${JWT_SECRET:...}) would be a committed secret.
        assertThat(shipped.getProperty("smarttriage.security.jwt-secret")).isEqualTo("${JWT_SECRET}");
        assertThat(shipped.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
    }

    @Test
    void devProfileDoesNotChangeSecuritySettings() {
        assertThat(dev.stringPropertyNames())
                .noneMatch(name -> name.startsWith("smarttriage.security") || name.startsWith("spring.security"));
    }

    @Test
    void tokensExpire() {
        assertThat(shipped.getProperty("smarttriage.security.token-validity")).isEqualTo("PT1H");
    }

    private static Properties load(String resource) {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            properties.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
        return properties;
    }
}
