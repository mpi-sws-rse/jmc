package org.mpi_sws.jmc.mp.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.MpTestSupport.ANY;
import static org.mpi_sws.jmc.mp.MpTestSupport.T1;
import static org.mpi_sws.jmc.mp.MpTestSupport.T2;
import static org.mpi_sws.jmc.mp.MpTestSupport.T3;
import static org.mpi_sws.jmc.mp.MpTestSupport.env;
import static org.mpi_sws.jmc.mp.MpTestSupport.envelopes;
import static org.mpi_sws.jmc.mp.MpTestSupport.msgIs;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.consistency.TaskClocks;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Envelope;
import org.mpi_sws.jmc.mp.events.MpMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Pins the purity obligations the semantic core is held to by plan/state/mp-contract.md §5 (the
 * dependency rule) and plan/state/state.md §3.1 / D8: {@code mp.events}, {@code mp.consistency} and
 * {@code mp.semantics} "must not import {@code org.mpi_sws.jmc.runtime.*},
 * {@code org.mpi_sws.jmc.api.*}, or {@code org.mpi_sws.jmc.strategies.*}; must hold no static
 * mutable state; must perform no I/O".
 *
 * <p>These are what let the reference enumerator and the explicit engine call the core as plain
 * JUnit with no agent and no runtime (route B), while a strategy calls the very same function on
 * route A — and they are what stops the two routes drifting into two encodings of one semantics.
 * They are also, indirectly, law L1 (state.md §1): a package with no state cannot accumulate a
 * decision.
 *
 * <p>plan/state/mp-contract.md §6.1 specifies these last two as ArchUnit tests. ArchUnit is not on
 * the {@code core} test classpath and this task may not add dependencies, so they are implemented
 * with reflection over the loaded classes (static fields) and a scan of the source tree plus the
 * compiled constant pools (imports and references). The two {@code …_discriminates…} halves feed
 * a known-bad control sample to each checker, so a checker that silently found nothing would fail.
 */
public class MpSemanticsPurityTest {

    /**
     * The four packages of the semantic core, as source-tree subdirectories of {@code mp/}.
     *
     * <p>{@code "graph"} was added in S2b, discharging the obligation S2 review round 1, n4 left
     * here. The list is explicit on purpose — {@code mp.adapter} will depend on {@code api.mp} and
     * must not be scanned — which means a new core package is silently unscanned until it is named;
     * mp-contract.md §5 lists exactly which packages the dependency rule binds, and this list must
     * be kept equal to it. {@link #coreSources} fails loudly on a missing directory, so a package
     * named here before it exists turns every purity test red rather than green.
     */
    private static final List<String> CORE_PACKAGES =
            List.of("events", "graph", "consistency", "semantics");

    /** The package prefixes the dependency rule forbids, in source (dotted) form. */
    private static final List<String> FORBIDDEN_DOTTED =
            List.of(
                    "org.mpi_sws.jmc.runtime.",
                    "org.mpi_sws.jmc.api.",
                    "org.mpi_sws.jmc.strategies.");

    /** The same prefixes in class-file (internal) form. */
    private static final List<String> FORBIDDEN_INTERNAL =
            List.of(
                    "org/mpi_sws/jmc/runtime/",
                    "org/mpi_sws/jmc/api/",
                    "org/mpi_sws/jmc/strategies/");

    /**
     * The class-file (internal) names by which a class would reach the outside world. The third
     * clause of the mp-contract.md §5 dependency rule — "must perform no I/O" — had no checker at
     * all before S2b; this is it, and it is a constant-pool scan for the same reason the import
     * scan has one: a fully-qualified {@code java.io.File} never appears in an import list.
     *
     * <p>{@code java/lang/System} covers {@code System.out}, {@code System.err},
     * {@code System.getenv} and {@code System.getProperty} in one string. It also covers
     * {@code System.arraycopy} and {@code System.nanoTime}, which are not I/O — if the core ever
     * needs one of those, this list needs a finer check (a method-reference scan) rather than an
     * exemption for the class.
     */
    private static final List<String> FORBIDDEN_IO_INTERNAL =
            List.of(
                    "java/io/",
                    "java/nio/file/",
                    "java/net/",
                    "java/lang/System",
                    "java/lang/Runtime");

    @Test
    void deliverable_isSideEffectFree_sameStateSameAnswerTwice() {
        TaskClocks.Emission first = TaskClocks.EMPTY.emit(T1.raw());
        TaskClocks.Emission second = first.clocks().emit(T1.raw());
        TaskClocks.Emission third = second.clocks().emit(T2.raw());

        Envelope cd1 = env(T1, 0, T3, CommunicationModel.CD, new Msg(1));
        Envelope cd2 = env(T1, 1, T3, CommunicationModel.CD, new Msg(2));
        Envelope cd3 = env(T2, 0, T3, CommunicationModel.CD, new Msg(3));
        Envelope p2p1 = env(T1, 2, T3, CommunicationModel.P2P, new Msg(1));
        Envelope mbox1 = env(T2, 1, T3, CommunicationModel.MBOX, new Msg(4));

        // Clock mode: withClocks(), because this state contains cd sends — and then every model's
        // emission must be stamped, which is why the p2p and mbox sends carry stamps too.
        Buffers b =
                Buffers.withClocks()
                        .emit(cd1, first.stamp())
                        .emit(cd2, second.stamp())
                        .emit(cd3, third.stamp())
                        .emit(p2p1, second.stamp())
                        .emit(mbox1, third.stamp());
        Buffers snapshot =
                Buffers.withClocks()
                        .emit(cd1, first.stamp())
                        .emit(cd2, second.stamp())
                        .emit(cd3, third.stamp())
                        .emit(p2p1, second.stamp())
                        .emit(mbox1, third.stamp());

        // A guard that counts its own invocations: the count must be the same on both calls, so
        // the second call cannot be served from a mutated buffer or a cache.
        AtomicLong calls = new AtomicLong();
        Predicate<MpMessage> counting =
                m -> {
                    calls.incrementAndGet();
                    return true;
                };

        for (CommunicationModel model : CommunicationModel.values()) {
            calls.set(0);
            List<Envelope> once = envelopes(Delivery.deliverable(b, model, T3, counting));
            long callsOnce = calls.get();

            calls.set(0);
            List<Envelope> twice = envelopes(Delivery.deliverable(b, model, T3, counting));

            assertEquals(once, twice, "same buffers, same model, same answer for " + model);
            assertEquals(callsOnce, calls.get(), "same guard evaluations for " + model);
            assertEquals(snapshot, b, "deliverable must not mutate the buffers (" + model + ")");
        }

        // Different guards over the same state do not disturb each other, in either order.
        List<Envelope> withAny = envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, ANY));
        assertEquals(
                List.of(cd1),
                envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, msgIs(1))));
        assertEquals(withAny, envelopes(Delivery.deliverable(b, CommunicationModel.CD, T3, ANY)));
        assertEquals(snapshot, b);

        // The answer is an unmodifiable view: a backend cannot mutate it into the next answer.
        assertThrows(
                UnsupportedOperationException.class,
                () -> Delivery.deliverable(b, CommunicationModel.CD, T3, ANY).add(null));

        // Nulls are rejected loudly rather than defaulted (contract §3.1's "no silent default").
        assertThrows(
                NullPointerException.class,
                () -> Delivery.deliverable(null, CommunicationModel.CD, T3, ANY));
        assertThrows(NullPointerException.class, () -> Delivery.deliverable(b, null, T3, ANY));
        assertThrows(
                NullPointerException.class,
                () -> Delivery.deliverable(b, CommunicationModel.CD, null, ANY));
        assertThrows(
                NullPointerException.class,
                () -> Delivery.deliverable(b, CommunicationModel.CD, T3, null));
    }

    @Test
    void semanticsPackages_haveNoStaticMutableFields() {
        List<Class<?>> classes = coreClasses();
        assertTrue(classes.size() >= 10, "expected the ten core types, found " + classes.size());

        List<String> violations = staticMutableFields(classes);
        assertEquals(List.of(), violations, "static mutable state in the semantic core");
    }

    @Test
    void semanticsPackages_doNotImportRuntimeApiOrStrategies() {
        List<Path> sources = coreSources();
        assertTrue(sources.size() >= 10, "expected ten source files, found " + sources.size());

        List<String> violations = new ArrayList<>();
        for (Path p : sources) {
            for (String line : readLines(p)) {
                violations.addAll(forbiddenImportsIn(p.getFileName().toString(), line));
            }
        }
        assertEquals(List.of(), violations, "forbidden imports in the semantic core");

        // Stronger than an import scan, and immune to a fully-qualified reference: the compiled
        // constant pool of every core class must mention no forbidden package either.
        List<String> byBytecode = new ArrayList<>();
        for (Class<?> c : coreClasses()) {
            String pool = constantPoolText(c);
            for (String forbidden : FORBIDDEN_INTERNAL) {
                if (pool.contains(forbidden)) {
                    byBytecode.add(c.getName() + " references " + forbidden);
                }
            }
        }
        assertEquals(List.of(), byBytecode, "forbidden references in the compiled semantic core");
    }

    /**
     * The third clause of the mp-contract.md §5 dependency rule: the semantic core "must perform no
     * I/O". A pure function of a state and a model has nothing to read a file, open a socket or
     * print for; a core that did any of those would also be the place where a backend's determinism
     * quietly stopped holding.
     *
     * <p>Checked over the compiled constant pool of every core class, nested classes included, and
     * fed a planted bad control at the end so that a scan finding nothing cannot pass for a scan
     * finding no violations.
     */
    @Test
    void semanticsPackages_performNoIo() {
        List<Class<?>> classes = coreClasses();
        assertTrue(classes.size() >= 10, "expected the core types, found " + classes.size());

        assertEquals(List.of(), ioReferencesIn(classes), "I/O in the semantic core");

        // Planted bad control: a nested class whose only body is System.out.println(...). That one
        // statement trips two of the five patterns — the field's owner java/lang/System and its
        // descriptor Ljava/io/PrintStream; — and nothing trips the other three.
        List<String> planted = ioReferencesIn(List.of(BadIoState.class));
        assertEquals(2, planted.size(), planted.toString());
        assertTrue(planted.toString().contains("java/io/"), planted.toString());
        assertTrue(planted.toString().contains("java/lang/System"), planted.toString());

        // …and a control that does not, so the scanner is not simply flagging everything.
        assertEquals(List.of(), ioReferencesIn(List.of(GoodStaticState.class)));
    }

    /**
     * Discriminating construction for this class: both checkers above are run over deliberately
     * bad control samples and must report exactly the violations planted in them. Without this,
     * a checker that scanned nothing — a wrong source root, an empty class list — would report a
     * green pass, which is precisely the failure mode plan/state/rose-state.md §S0.1 warns about
     * for silently skipped tests.
     */
    @Test
    void purityChecks_discriminate_knownBadControlSamplesAreRejected() {
        // Control 1: a class with a static mutable field of each shape the checker looks for.
        List<String> found = staticMutableFields(List.of(BadStaticState.class));
        assertEquals(4, found.size(), "expected four planted violations, got " + found);
        assertTrue(found.toString().contains("counter"), found.toString());
        assertTrue(found.toString().contains("CACHE"), found.toString());
        assertTrue(found.toString().contains("NAMES"), found.toString());
        assertTrue(found.toString().contains("BUFFER"), found.toString());

        // ...and the same checker passes a class whose statics are immutable values.
        assertEquals(List.of(), staticMutableFields(List.of(GoodStaticState.class)));

        // Control 2: the import scanner on lines that must and must not be rejected.
        assertEquals(
                1, forbiddenImportsIn("X.java", "import org.mpi_sws.jmc.runtime.JmcRuntime;").size());
        assertEquals(
                1,
                forbiddenImportsIn("X.java", "import static org.mpi_sws.jmc.api.JmcObject.x;").size());
        assertEquals(
                1,
                forbiddenImportsIn(
                                "X.java",
                                "  import  org.mpi_sws.jmc.strategies.SchedulingStrategy ;")
                        .size());
        assertEquals(0, forbiddenImportsIn("X.java", "import org.mpi_sws.jmc.mp.events.Tid;").size());
        assertEquals(0, forbiddenImportsIn("X.java", "import java.util.List;").size());
        assertEquals(
                0,
                forbiddenImportsIn("X.java", " * from {@code org.mpi_sws.jmc.api.mp.MpProcess}").size(),
                "a Javadoc mention is not an import — Tid.java has one on purpose");

        // Control 3: the constant-pool scanner really sees a reference. This test class itself
        // does not touch the forbidden packages, so use a class that plainly does not either,
        // plus a positive control on a string the pool is known to contain.
        assertTrue(constantPoolText(Delivery.class).contains("org/mpi_sws/jmc/mp/events/"));
        assertFalse(constantPoolText(Delivery.class).contains("org/mpi_sws/jmc/runtime/"));
    }

    // ---- the two checkers, written so they can be pointed at a control sample ----

    /**
     * Returns one description per static field that is not an immutable value: any non-final
     * static field, and any static final field whose declared type is an array, a
     * {@link Collection}, a {@link Map} or a {@code java.util.concurrent.atomic} box. Synthetic
     * fields (an enum's {@code $VALUES}, for instance) and enum constants are skipped.
     */
    private static List<String> staticMutableFields(List<Class<?>> classes) {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : classes) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || f.isEnumConstant()) {
                    continue;
                }
                if (!Modifier.isFinal(f.getModifiers())) {
                    violations.add(c.getName() + "." + f.getName() + " is static but not final");
                    continue;
                }
                Class<?> t = f.getType();
                if (t.isArray()
                        || Collection.class.isAssignableFrom(t)
                        || Map.class.isAssignableFrom(t)
                        || t.getName().startsWith("java.util.concurrent.atomic.")) {
                    violations.add(
                            c.getName() + "." + f.getName() + " is a static mutable " + t.getName());
                }
            }
        }
        return violations;
    }

    /** Returns one description per {@link #FORBIDDEN_IO_INTERNAL} name a class's pool mentions. */
    private static List<String> ioReferencesIn(List<Class<?>> classes) {
        List<String> violations = new ArrayList<>();
        for (Class<?> c : classes) {
            String pool = constantPoolText(c);
            for (String forbidden : FORBIDDEN_IO_INTERNAL) {
                if (pool.contains(forbidden)) {
                    violations.add(c.getName() + " references " + forbidden);
                }
            }
        }
        return violations;
    }

    /** Returns a description if {@code line} is an import of a forbidden package, else nothing. */
    private static List<String> forbiddenImportsIn(String file, String line) {
        String trimmed = line.strip();
        if (!trimmed.startsWith("import ")) {
            return List.of();
        }
        String imported = trimmed.substring("import ".length()).strip();
        if (imported.startsWith("static ")) {
            imported = imported.substring("static ".length()).strip();
        }
        for (String forbidden : FORBIDDEN_DOTTED) {
            if (imported.startsWith(forbidden)) {
                return List.of(file + ": " + trimmed);
            }
        }
        return List.of();
    }

    // ---- locating the core, from the source tree and from the class path ----

    private static List<Path> coreSources() {
        Path root = sourceRoot();
        List<Path> out = new ArrayList<>();
        for (String pkg : CORE_PACKAGES) {
            Path dir = root.resolve(pkg);
            assertTrue(Files.isDirectory(dir), "no such package directory: " + dir);
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".java")).forEach(out::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }

    private static List<Class<?>> coreClasses() {
        List<Class<?>> out = new ArrayList<>();
        for (Path p : coreSources()) {
            String simple = p.getFileName().toString().replace(".java", "");
            if (simple.equals("package-info")) {
                continue;
            }
            String pkg = p.getParent().getFileName().toString();
            Class<?> c;
            try {
                c = Class.forName("org.mpi_sws.jmc.mp." + pkg + "." + simple);
            } catch (ClassNotFoundException e) {
                throw new AssertionError("source file with no compiled class: " + p, e);
            }
            out.add(c);
            collectNested(c, out);
        }
        return out;
    }

    private static void collectNested(Class<?> c, List<Class<?>> out) {
        for (Class<?> nested : c.getDeclaredClasses()) {
            out.add(nested);
            collectNested(nested, out);
        }
    }

    /**
     * Locates {@code core/src/main/java/org/mpi_sws/jmc/mp} by walking up from the working
     * directory, and fails loudly rather than silently scanning nothing.
     */
    private static Path sourceRoot() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path dir = here; dir != null; dir = dir.getParent()) {
            for (String prefix : List.of("src/main/java", "core/src/main/java")) {
                Path candidate = dir.resolve(prefix).resolve("org/mpi_sws/jmc/mp");
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
        }
        throw new AssertionError("could not locate org/mpi_sws/jmc/mp sources from " + here);
    }

    /** Reads a class file's raw bytes as Latin-1 text, so the constant pool can be searched. */
    private static String constantPoolText(Class<?> c) {
        String resource = "/" + c.getName().replace('.', '/') + ".class";
        try (InputStream in = c.getResourceAsStream(resource)) {
            assertNotNull(in, "no class file for " + c.getName());
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> readLines(Path p) {
        try {
            return Files.readAllLines(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- control samples for the discriminating test; never scanned by the real checks ----

    private static final class BadStaticState {
        private static int counter;
        private static final int[] CACHE = new int[4];
        private static final List<String> NAMES = new ArrayList<>();
        private static final Map<String, String> BUFFER = new java.util.HashMap<>();

        private BadStaticState() {}
    }

    /** Planted control for {@link #semanticsPackages_performNoIo}: one reference to {@code System}. */
    private static final class BadIoState {

        private BadIoState() {}

        static void report(String what) {
            System.out.println(what);
        }
    }

    private static final class GoodStaticState {
        private static final String NAME = "ok";
        private static final long COUNT = 3L;
        private static final Buffers EMPTY = Buffers.withClocks();

        private GoodStaticState() {}
    }
}
