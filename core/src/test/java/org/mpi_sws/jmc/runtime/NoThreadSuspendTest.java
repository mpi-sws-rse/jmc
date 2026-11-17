package org.mpi_sws.jmc.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * No source of this module calls {@code Thread.suspend()} or {@code Thread.resume()}: both throw
 * {@link UnsupportedOperationException} from JDK 20 on, so a test that needs to hold a thread inside
 * the runtime's wait uses the runtime's wake hook for tests instead
 * ({@code TaskManager.setWakeHookForTesting}).
 */
class NoThreadSuspendTest {

    private static final Pattern CALL = Pattern.compile("\\.(suspend|resume)\\(\\)");

    @Test
    void noSourceCallsThreadSuspendOrResume() throws IOException {
        List<String> calls = new ArrayList<>();
        for (String root : List.of("src/main/java", "src/test/java")) {
            try (Stream<Path> files = Files.walk(Path.of(root))) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    List<String> lines = Files.readAllLines(f);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i).trim();
                        if (!line.startsWith("*") && !line.startsWith("//") && CALL.matcher(line).find()) {
                            calls.add(f + ":" + (i + 1));
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), calls);
    }
}
