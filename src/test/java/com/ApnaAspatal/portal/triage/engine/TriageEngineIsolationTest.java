package com.ApnaAspatal.portal.triage.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Enforces that the engine stays pure decision logic.
 *
 * <p>A unit test cannot prove code never reads the clock, so this reads the
 * engine's source and fails the build if it references the clock, Spring, JPA,
 * or repositories. It is a deliberately simple textual guard, not a full
 * architecture test - it will also flag these words in comments.
 */
class TriageEngineIsolationTest {

    private static final Path ENGINE_SOURCES =
            Path.of("src/main/java/com/ApnaAspatal/portal/triage/engine");

    private static final List<String> FORBIDDEN = List.of(
            ".now(",               // system clock: breaks determinism
            "java.time.Clock",     // clock injection: same problem, one step removed
            "org.springframework", // framework: the engine is plain Java
            "jakarta.persistence", // JPA: the engine must not touch persistence
            "Repository");         // data access: decisions must depend only on facts

    @Test
    void engineSourcesHaveNoClockSpringOrPersistenceDependencies() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.list(ENGINE_SOURCES)) {
            sources = files.filter(path -> path.toString().endsWith(".java")).toList();
        }

        assertThat(sources).as("engine sources found at %s", ENGINE_SOURCES.toAbsolutePath()).isNotEmpty();

        for (Path source : sources) {
            String content = Files.readString(source);
            for (String forbidden : FORBIDDEN) {
                assertThat(content)
                        .as("%s must not reference %s", source.getFileName(), forbidden)
                        .doesNotContain(forbidden);
            }
        }
    }
}
