package org.mpi_sws.jmc.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.runtime.scheduling.MpDeliveryValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoiceValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoiceValueFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The structural obligations {@code org.mpi_sws.jmc.api.mp} and its answer type are held to —
 * plan/state/mp-contract.md §4.2 and §3.2, and law L1 (plan/state/state.md §1).
 *
 * <p>Three separate claims, each about a way the first implementation went wrong:
 *
 * <ul>
 *   <li><b>The API does not compute.</b> {@code api.mp} must not reach into
 *       {@code mp.semantics} or {@code mp.graph}. A receive that could call
 *       {@code Delivery.deliverable} could filter its own candidates, and filtering to one is
 *       deciding — rose-state.md F1, the defect the package was rewritten to make structurally
 *       impossible. The obligation is on the <em>import graph</em>, not on today's method bodies,
 *       because it is the reachability that has to be absent.</li>
 *   <li><b>The API holds nothing.</b> {@code Channel} declares no static fields at all, so there is
 *       nowhere for communication state to accumulate (§4.2: "This class has no static state and no
 *       static initializer. It cannot: there is nothing for it to hold"). Law L2 puts the buffers in
 *       the strategy.</li>
 *   <li><b>The answer is an identity, not a payload.</b> {@code MpDeliveryValue} serializes
 *       {@code (src, sendIndex)} — the Must {@code Event.Key} — and never the message, because
 *       replay re-executes the program and the sender regenerates it (§3.2).</li>
 * </ul>
 *
 * <p>A plain JUnit test in {@code core}: reflection over loaded classes, a scan of the source tree,
 * and pure calls on the value type. No runtime, no agent, no strategy. ArchUnit is not on this
 * module's test classpath and this task may not add dependencies, so the import check is a source
 * scan — the same technique {@code MpSemanticsPurityTest} uses for the {@code mp.*} dependency rule,
 * with the same planted-control discipline.
 */
public class MpApiArchitectureTest {

    /** The package prefixes {@code api.mp} must not reach, in source (dotted) form. */
    private static final List<String> FORBIDDEN =
            List.of("org.mpi_sws.jmc.mp.semantics", "org.mpi_sws.jmc.mp.graph");

    /**
     * No file of {@code org.mpi_sws.jmc.api.mp} names {@code mp.semantics} or {@code mp.graph}
     * anywhere in its source — neither as an import nor as a fully qualified reference.
     *
     * <p>The check is over source text rather than the compiled constant pool because a
     * {@code static} call that the compiler inlines to a constant would leave no trace in the pool,
     * and a Javadoc {@code @link} that a reader would follow leaves none either; the point of the
     * rule is that the two packages are not reachable <em>and are not thought of as reachable</em>
     * from here.
     *
     * <p>Two discriminating halves. The scan is asserted to have found the package's files at all —
     * a scan that silently found nothing would otherwise report success. And the same matcher is
     * run against a line that <em>does</em> contain each forbidden prefix, so a matcher that never
     * matched anything cannot pass either. The permitted dependency, {@code mp.events}, shares the
     * prefix {@code org.mpi_sws.jmc.mp} with both forbidden packages and is asserted to be present,
     * which pins that the rule is about the subpackages and not about {@code mp} as a whole.
     */
    @Test
    public void apiMpPackage_doesNotImportMpSemanticsOrMpGraph() {
        List<Path> sources = apiMpSources();
        assertTrue(sources.size() >= 4, "expected the api.mp sources; found " + sources);

        boolean sawEventsImport = false;
        for (Path source : sources) {
            String text = read(source);
            for (String forbidden : FORBIDDEN) {
                assertFalse(
                        text.contains(forbidden),
                        source.getFileName() + " must not reach " + forbidden + " (L1, F1)");
            }
            sawEventsImport |= text.contains("org.mpi_sws.jmc.mp.events");
        }

        assertTrue(
                sawEventsImport,
                "api.mp is expected to depend on mp.events; the rule is about mp.semantics and"
                        + " mp.graph, not about mp as a whole");

        // The matcher itself must be capable of matching.
        for (String forbidden : FORBIDDEN) {
            assertTrue(
                    ("import " + forbidden + ".Something;").contains(forbidden),
                    "the containment check must be able to detect " + forbidden);
        }
    }

    /**
     * {@code Channel} declares no static fields — not a cache, not a mailbox, not a registry, not a
     * counter, and not a logger either. Its state is the empty set (§4.2, L2).
     *
     * <p>Stated as "no declared fields at all" rather than "no <em>mutable</em> static fields",
     * which is the stronger and simpler claim the contract actually makes and the only one that has
     * no loophole: a {@code static final Map} is final and would pass a mutability check while
     * holding every message in the program. The discriminating half asserts the reflection really
     * sees fields when there are some, using {@code MpDeliveryValue} — which does declare static
     * fields — as the positive control.
     */
    @Test
    public void channel_hasNoStaticFields() {
        assertEquals(
                0,
                Channel.class.getDeclaredFields().length,
                "Channel must declare no fields at all; it declares "
                        + List.of(Channel.class.getDeclaredFields()));

        assertTrue(
                MpDeliveryValue.class.getDeclaredFields().length > 0,
                "the control: getDeclaredFields must be able to see fields when there are some");
    }

    /**
     * {@code MpDeliveryValue.toJson()} writes the send's identity and never its value; {@code ⊥}
     * writes {@code {"bottom": true}} and nothing else (§3.2).
     *
     * <p>The value is deliberately absent because replay re-executes the program, so the sender
     * regenerates the message and only the <em>choice</em> has to survive to JSON. Serializing an
     * arbitrary user message would be fragile — a message is any {@code MpMessage} the program
     * defines — and would record data replay does not need.
     *
     * <p>Non-vacuous by construction: the message used carries a distinctive payload, and the
     * assertion is that the rendered JSON does not contain it anywhere, so the check would fail
     * against any serialization that leaked the value under any key. The exact key set is asserted
     * too, so a value smuggled in under a different name fails as well.
     */
    @Test
    public void mpDeliveryValue_toJson_omitsTheValue() {
        MpDeliveryValue delivery = MpDeliveryValue.of(Tid.of(3L), 7L, new Secret(987654321));
        JsonObject json = delivery.toJson().getAsJsonObject();

        assertEquals(
                java.util.Set.of("src", "sendIndex"),
                json.keySet(),
                "a delivery serializes exactly the send's identity");
        assertEquals(3L, json.get("src").getAsLong(), "src");
        assertEquals(7L, json.get("sendIndex").getAsLong(), "sendIndex");
        assertFalse(
                json.toString().contains("987654321"),
                "the message value must not appear in the JSON: " + json);
        assertFalse(
                json.toString().contains("Secret"),
                "not even the message's type may appear in the JSON: " + json);

        JsonObject bottom = MpDeliveryValue.bottom().toJson().getAsJsonObject();
        assertEquals(java.util.Set.of("bottom"), bottom.keySet(), "⊥ serializes as the marker alone");
        assertTrue(bottom.get("bottom").getAsBoolean(), "the marker is true");

        // ⊥ delivers no message, and asking for one is an error rather than a null.
        assertThrows(IllegalStateException.class, () -> MpDeliveryValue.bottom().value());
    }

    /**
     * The adapter registered for {@code "mp-delivery"} reconstructs what {@code toJson} wrote:
     * {@code (src, sendIndex)} for a delivery, {@code ⊥} for the marker.
     *
     * <p>Going through {@link SchedulingChoiceValueFactory#create} rather than calling the adapter
     * directly is the point of the test: {@code fromJson} is package-private on
     * {@code SchedulingChoiceValueAdapter}, and what a recorded trace actually exercises is the
     * factory's type-tag lookup. So this pins the registration as well as the parsing — a value type
     * whose adapter was never registered would fail here with "No adapter registered", which is the
     * failure mode that would otherwise only appear when a trace was replayed.
     *
     * <p>The value cannot round-trip and is not meant to: the adapter substitutes a placeholder that
     * a replaying strategy must replace from its own buffers. That is asserted explicitly, so the
     * placeholder cannot quietly reach a task and be mistaken for a real message. The malformed
     * input is the discriminating half: a JSON object with neither shape is rejected rather than
     * silently read as {@code ⊥}.
     */
    @Test
    public void mpDeliveryValueAdapter_roundTripsIdentity() {
        assertTrue(
                SchedulingChoiceValueFactory.containsType(MpDeliveryValue.TYPE),
                "the adapter must be registered under " + MpDeliveryValue.TYPE);

        MpDeliveryValue original = MpDeliveryValue.of(Tid.of(5L), 2L, new Secret(1));
        SchedulingChoiceValue reconstructed =
                SchedulingChoiceValueFactory.create(MpDeliveryValue.TYPE, original.toJson());

        assertTrue(reconstructed instanceof MpDeliveryValue, "the factory returns the right type");
        MpDeliveryValue delivery = (MpDeliveryValue) reconstructed;
        assertFalse(delivery.isBottom(), "a delivery must not come back as ⊥");
        assertEquals(Tid.of(5L), delivery.src(), "src survives");
        assertEquals(2L, delivery.sendIndex(), "sendIndex survives");
        assertEquals(
                original.toJson(),
                delivery.toJson(),
                "the identity is exactly what round-trips, so re-serializing is a fixed point");
        assertFalse(
                delivery.value() instanceof Secret,
                "the value does not round-trip: a placeholder stands in until the strategy resolves"
                        + " the send from its buffers");

        SchedulingChoiceValue bottom =
                SchedulingChoiceValueFactory.create(
                        MpDeliveryValue.TYPE, MpDeliveryValue.bottom().toJson());
        assertTrue(((MpDeliveryValue) bottom).isBottom(), "⊥ round-trips as ⊥");

        JsonElement malformed = JsonParser.parseString("{\"src\": 5}");
        assertThrows(
                IllegalArgumentException.class,
                () -> SchedulingChoiceValueFactory.create(MpDeliveryValue.TYPE, malformed),
                "a JSON object of neither shape must be rejected, not read as ⊥");
    }

    /** A message with a distinctive payload, so a leak into the JSON is detectable by search. */
    private record Secret(int magic) implements MpMessage {}

    /** The {@code .java} files of {@code org.mpi_sws.jmc.api.mp}. */
    private static List<Path> apiMpSources() {
        Path dir = sourceRoot().resolve("org/mpi_sws/jmc/api/mp");
        assertTrue(Files.isDirectory(dir), "no such package directory: " + dir);
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".java")).forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** Reads a source file as UTF-8. */
    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Locates {@code core/src/main/java} by walking up from the working directory, and fails loudly
     * rather than silently scanning nothing.
     */
    private static Path sourceRoot() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path dir = here; dir != null; dir = dir.getParent()) {
            for (String prefix : List.of("src/main/java", "core/src/main/java")) {
                Path candidate = dir.resolve(prefix).resolve("org/mpi_sws/jmc/api/mp");
                if (Files.isDirectory(candidate)) {
                    return dir.resolve(prefix);
                }
            }
        }
        throw new AssertionError("could not locate org/mpi_sws/jmc/api/mp sources from " + here);
    }
}
