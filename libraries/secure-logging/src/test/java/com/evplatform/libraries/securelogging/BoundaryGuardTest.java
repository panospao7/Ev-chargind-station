package com.evplatform.libraries.securelogging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Architecture boundary guard (ENG-001 doc §4.1, packet AC-01): the library's
 * main sources must not import application modules or business frameworks.
 * Zero-dependency source scan; runs in every build.
 */
class BoundaryGuardTest {

    private static final List<String> FORBIDDEN = List.of(
            "com.evplatform.services",
            "com.evplatform.apps",
            "com.evplatform.simulator",
            "org.springframework",
            "jakarta.persistence",
            "jakarta.transaction");

    @Test
    void mainSourcesContainNoForbiddenImports() throws IOException {
        Path main = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(main)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    String src = Files.readString(p);
                    for (String forbidden : FORBIDDEN) {
                        if (src.contains(forbidden)) {
                            fail(p + " references forbidden dependency: " + forbidden);
                        }
                    }
                } catch (IOException e) {
                    fail("cannot read " + p);
                }
            });
        }
    }
}
