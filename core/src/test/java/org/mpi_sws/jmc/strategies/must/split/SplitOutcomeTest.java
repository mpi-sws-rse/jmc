package org.mpi_sws.jmc.strategies.must.split;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.strategies.mp.MpOutcome;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * How a split run's abort is classified: a coordinator failure to read or write a file is a refusal, also when
 * {@link SplitCoordinator#writeAtomically} or {@link SplitCoordinator#readJson} wraps it, any other operational
 * failure leaves the run incomplete; a worker's fail marker carries its outcome to the coordinator.
 */
class SplitOutcomeTest {

    private static Path dir(String name) throws Exception {
        Path d = Path.of("build/test-results/split-outcome", name).toAbsolutePath();
        Files.createDirectories(d);
        try (var s = Files.list(d)) {
            for (Path p : s.toList()) {
                Files.delete(p);
            }
        }
        return d;
    }

    @Test
    void coordinatorFailures_wrappedIoIsRefused_otherwiseIncomplete() throws Exception {
        Path d = dir("abort");
        IllegalStateException write = assertThrows(IllegalStateException.class,
                () -> SplitCoordinator.writeAtomically(d.resolve("missing-dir").resolve("STOP"), new JsonObject()));
        assertEquals(MpOutcome.REFUSED, SplitCoordinator.abortOutcome(write));
        IllegalStateException read = assertThrows(IllegalStateException.class,
                () -> SplitCoordinator.readJson(d.resolve("absent.json")));
        assertEquals(MpOutcome.REFUSED, SplitCoordinator.abortOutcome(read));
        Files.writeString(d.resolve("bad.json"), "{not json", StandardCharsets.UTF_8);
        IllegalStateException parse = assertThrows(IllegalStateException.class,
                () -> SplitCoordinator.readJson(d.resolve("bad.json")));
        assertEquals(MpOutcome.INCOMPLETE, SplitCoordinator.abortOutcome(parse));
        assertEquals(MpOutcome.INCOMPLETE, SplitCoordinator.abortOutcome(new IllegalStateException("other")));
    }

    @Test
    void failMarkers_carryTheirOutcome_toTheCoordinator() throws Exception {
        Path d = dir("markers");
        for (MpOutcome o : MpOutcome.values()) {
            SplitWorker.writeFailMarker(d.resolve("worker-" + o.ordinal() + ".fail.json"), new JsonObject(),
                    "a " + o + " failure", o);
            assertEquals(o, SplitCoordinator.failMarkerOutcome(d, "worker-" + o.ordinal() + ".fail.json"));
        }
        JsonObject old = new JsonObject();
        old.addProperty("reason", "a marker without an outcome");
        SplitCoordinator.writeAtomically(d.resolve("worker-9.fail.json"), old);
        assertEquals(MpOutcome.INCOMPLETE, SplitCoordinator.failMarkerOutcome(d, "worker-9.fail.json"));
    }
}
