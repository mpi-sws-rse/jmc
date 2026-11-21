package org.mpi_sws.jmc.mp.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.GraphTestSupport.block;
import static org.mpi_sws.jmc.mp.GraphTestSupport.nondet;
import static org.mpi_sws.jmc.mp.GraphTestSupport.recv;
import static org.mpi_sws.jmc.mp.GraphTestSupport.send;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.Tid;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * JMC-SPEED-2 tester round 01 item 4: J3's porf (vector clocks in a Kahn order of po ∪ rf ∪ hb,
 * the closure on a cycle) against a reference closure computed here from the graph's public
 * contents ({@code threads()}, {@code rf()}, {@code hb()}): for every graph of the C1 plan rev 3
 * §3c general-builder matrix and for random graphs, {@code porfAcyclic()} and {@code porfBefore} on
 * every ordered pair of events, and on absent keys (unknown tid, negative index, index past the
 * thread, {@code Long.MIN_VALUE}/{@code MAX_VALUE}), must equal the reference. Derived: {@code a porf
 * b} iff b is reachable from a by one or more po, rf or hb edges (rf: from each receive's send to
 * that receive, so every reader of one send gets an edge); acyclic iff no event reaches itself; an
 * absent key is porf-related to nothing. {@link J3Probe} runs the same matrix in fresh JVMs with
 * {@code -Djmc.must.checkPrefixes=true} (the clock answers then also meet {@code checkPorf}) and
 * with {@code -ea} alone (the check stays off).
 */
public class J3PorfTest {

    static final CommunicationModel A = CommunicationModel.ASYN;

    /** The reference: reachability by one or more edges. */
    static Map<EventKey, Set<EventKey>> reference(ExecutionGraph g) {
        Map<EventKey, List<EventKey>> succ = new HashMap<>();
        for (List<GraphEvent> l : g.threads().values()) {
            for (int i = 0; i + 1 < l.size(); i++) {
                succ.computeIfAbsent(l.get(i).key(), k -> new ArrayList<>()).add(l.get(i + 1).key());
            }
        }
        for (GraphEvent r : g.receives()) {
            g.rf(r.key()).ifPresent(s -> succ.computeIfAbsent(s, k -> new ArrayList<>()).add(r.key()));
        }
        for (ExecutionGraph.Edge e : g.hb()) {
            succ.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
        }
        Map<EventKey, Set<EventKey>> out = new LinkedHashMap<>();
        for (GraphEvent ev : g.eventsInInsertionOrder()) {
            Set<EventKey> seen = new HashSet<>();
            Deque<EventKey> todo = new ArrayDeque<>(succ.getOrDefault(ev.key(), List.of()));
            while (!todo.isEmpty()) {
                EventKey k = todo.pop();
                if (seen.add(k)) {
                    todo.addAll(succ.getOrDefault(k, List.of()));
                }
            }
            out.put(ev.key(), seen);
        }
        return out;
    }

    /** Checks one graph; returns "cyclic"/"acyclic" and the number of pairs. */
    static String check(String name, ExecutionGraph g) {
        Map<EventKey, Set<EventKey>> ref = reference(g);
        boolean acyclic = ref.entrySet().stream().noneMatch(e -> e.getValue().contains(e.getKey()));
        List<EventKey> keys = new ArrayList<>(ref.keySet());
        List<EventKey> absent = new ArrayList<>();
        Set<Long> tids = new HashSet<>(g.threads().keySet());
        long unknown = 7_777_777L;
        while (tids.contains(unknown)) {
            unknown++;
        }
        absent.add(new EventKey(unknown, 0));
        absent.add(new EventKey(Long.MIN_VALUE, 0));
        for (Map.Entry<Long, List<GraphEvent>> t : g.threads().entrySet()) {
            absent.add(new EventKey(t.getKey(), -1));
            absent.add(new EventKey(t.getKey(), -2));
            absent.add(new EventKey(t.getKey(), t.getValue().size()));
            absent.add(new EventKey(t.getKey(), Long.MAX_VALUE));
            absent.add(new EventKey(t.getKey(), Long.MIN_VALUE));
        }
        // the first query decides the representation (and, under the property, runs the check)
        boolean gotAcyclic = g.porfAcyclic();
        assertEquals(acyclic, gotAcyclic, name + ": porfAcyclic");
        int pairs = 0;
        for (EventKey a : keys) {
            for (EventKey b : keys) {
                assertEquals(ref.get(a).contains(b), g.porfBefore(a, b), name + ": " + a + " porf " + b);
                pairs++;
            }
            for (EventKey x : absent) {
                assertFalse(g.porfBefore(a, x), name + ": " + a + " porf absent " + x);
                assertFalse(g.porfBefore(x, a), name + ": absent " + x + " porf " + a);
            }
        }
        for (EventKey x : absent) {
            for (EventKey y : absent) {
                assertFalse(g.porfBefore(x, y), name + ": absent " + x + " porf absent " + y);
            }
        }
        assertEquals(acyclic, g.porfAcyclic(), name + ": porfAcyclic again");
        return (acyclic ? "acyclic" : "cyclic") + " events=" + keys.size() + " pairs=" + pairs;
    }

    static EventKey k(long t, long i) {
        return new EventKey(t, i);
    }

    static Tid t(long raw) {
        return Tid.of(raw);
    }

    /** The general-builder matrix (C1 plan rev 3 §3c), by name. */
    static Map<String, Supplier<ExecutionGraph>> matrix() {
        Map<String, Supplier<ExecutionGraph>> m = new LinkedHashMap<>();
        m.put("empty", () -> new ExecutionGraph.Builder().build());
        m.put("one-event", () -> new ExecutionGraph.Builder().add(send(t(1), 0, A, t(2), new Msg(1))).build());
        m.put("hb-self-loop", () -> new ExecutionGraph.Builder()
                .add(send(t(1), 0, A, t(2), new Msg(1))).add(recv(t(2), 0, A))
                .readsFrom(k(2, 0), k(1, 0)).hb(k(2, 0), k(2, 0)).build());
        m.put("hb-self-loop-isolated", () -> new ExecutionGraph.Builder()
                .add(nondet(t(1), 0, 2, 0)).add(nondet(t(3), 0, 2, 1)).hb(k(3, 0), k(3, 0)).build());
        m.put("cycle-po-rf", () -> new ExecutionGraph.Builder()
                .add(recv(t(1), 0, A)).add(send(t(1), 1, A, t(2), new Msg(1)))
                .add(recv(t(2), 0, A)).add(send(t(2), 1, A, t(1), new Msg(2)))
                .readsFrom(k(1, 0), k(2, 1)).readsFrom(k(2, 0), k(1, 1)).build());
        m.put("cycle-rf-hb", () -> new ExecutionGraph.Builder()
                .add(send(t(1), 0, A, t(2), new Msg(1))).add(recv(t(2), 0, A))
                .readsFrom(k(2, 0), k(1, 0)).hb(k(2, 0), k(1, 0)).build());
        m.put("cycle-po-hb", () -> new ExecutionGraph.Builder()
                .add(nondet(t(1), 0, 2, 0)).add(nondet(t(1), 1, 2, 1)).hb(k(1, 1), k(1, 0)).build());
        m.put("cycle-po-rf-hb-three-threads", () -> new ExecutionGraph.Builder()
                .add(send(t(1), 0, A, t(2), new Msg(1))).add(recv(t(2), 0, A)).add(nondet(t(2), 1, 2, 0))
                .add(nondet(t(3), 0, 2, 1)).add(block(t(3), 1)).add(nondet(t(1), 1, 3, 2))
                .readsFrom(k(2, 0), k(1, 0)).hb(k(2, 1), k(3, 0)).hb(k(3, 1), k(1, 0)).build());
        m.put("cycle-plus-acyclic-component", () -> new ExecutionGraph.Builder()
                .add(send(t(1), 0, A, t(2), new Msg(1))).add(recv(t(2), 0, A))
                .add(nondet(t(5), 0, 2, 0)).add(nondet(t(5), 1, 2, 0)).add(nondet(t(6), 0, 2, 0))
                .readsFrom(k(2, 0), k(1, 0)).hb(k(5, 1), k(5, 0)).hb(k(1, 0), k(6, 0)).build());
        m.put("several-readers-of-one-send", () -> new ExecutionGraph.Builder()
                .add(send(t(1), 0, A, t(2), new Msg(1))).add(recv(t(2), 0, A)).add(recv(t(3), 0, A))
                .add(nondet(t(2), 1, 2, 0)).add(nondet(t(3), 1, 2, 0)).add(recv(t(4), 0, A))
                .readsFrom(k(2, 0), k(1, 0)).readsFrom(k(3, 0), k(1, 0)).readsFrom(k(4, 0), k(1, 0)).build());
        m.put("several-readers-later-send", () -> new ExecutionGraph.Builder()
                .add(recv(t(2), 0, A)).add(recv(t(3), 0, A)).add(nondet(t(1), 0, 2, 0))
                .add(send(t(1), 1, A, t(2), new Msg(1)))
                .readsFrom(k(3, 0), k(1, 1)).readsFrom(k(2, 0), k(1, 1)).build());
        m.put("duplicate-edges", () -> new ExecutionGraph.Builder()
                .add(send(t(1), 0, A, t(2), new Msg(1))).add(send(t(1), 1, A, t(2), new Msg(2)))
                .add(recv(t(2), 0, A)).add(recv(t(2), 1, A))
                .readsFrom(k(2, 0), k(1, 0)).readsFrom(k(2, 1), k(1, 1))
                .hb(k(1, 0), k(1, 1)).hb(k(1, 0), k(2, 0)).hb(k(1, 0), k(2, 0)).hb(k(2, 0), k(2, 1)).build());
        m.put("bottom-and-unread", () -> new ExecutionGraph.Builder()
                .add(send(t(1), 0, A, t(2), new Msg(1))).add(recv(t(2), 0, A)).add(recv(t(2), 1, A))
                .readsBottom(k(2, 0)).build());
        m.put("disconnected", () -> new ExecutionGraph.Builder()
                .add(send(t(1), 0, A, t(2), new Msg(1))).add(recv(t(2), 0, A))
                .add(send(t(8), 0, A, t(9), new Msg(1))).add(recv(t(9), 0, A)).add(nondet(t(9), 1, 2, 0))
                .add(block(t(10), 0)).readsFrom(k(2, 0), k(1, 0)).readsFrom(k(9, 0), k(8, 0)).build());
        m.put("sparse-tids", () -> new ExecutionGraph.Builder()
                .add(send(t(1_000_000_007L), 0, A, t(Long.MAX_VALUE), new Msg(1)))
                .add(recv(t(Long.MAX_VALUE), 0, A)).add(nondet(t(0), 0, 2, 0))
                .add(send(t(Long.MAX_VALUE), 1, A, t(0), new Msg(2))).add(recv(t(0), 1, A))
                .add(nondet(t(-5), 0, 2, 1)).add(nondet(t(3), 0, 2, 0))
                .readsFrom(k(Long.MAX_VALUE, 0), k(1_000_000_007L, 0)).readsFrom(k(0, 1), k(Long.MAX_VALUE, 1))
                .hb(k(0, 1), k(-5, 0)).hb(k(-5, 0), k(3, 0)).build());
        m.put("spawn-join-hb", () -> new ExecutionGraph.Builder()
                .add(nondet(t(1), 0, 2, 0)).add(send(t(2), 0, A, t(3), new Msg(1))).add(recv(t(3), 0, A))
                .add(nondet(t(1), 1, 2, 0))
                .readsFrom(k(3, 0), k(2, 0)).hb(k(1, 0), k(2, 0)).hb(k(1, 0), k(3, 0)).hb(k(2, 0), k(1, 1))
                .hb(k(3, 0), k(1, 1)).build());
        m.put("extended-by-builder-copy", () -> {
            ExecutionGraph g0 = new ExecutionGraph.Builder().add(send(t(1), 0, A, t(2), new Msg(1)))
                    .add(recv(t(2), 0, A)).build();
            assertTrue(g0.porfAcyclic());
            return new ExecutionGraph.Builder(g0).readsFrom(k(2, 0), k(1, 0)).add(nondet(t(2), 1, 2, 0))
                    .hb(k(2, 1), k(1, 0)).build();
        });
        return m;
    }

    /** A random graph: up to 6 threads with sparse ids, random interleaving, rf to any send, random hb. */
    static ExecutionGraph randomGraph(Random r) {
        long[] pool = {0, 1, 2, 3, 17, 42, 1_000_000_007L, Long.MAX_VALUE, -9};
        int nt = 1 + r.nextInt(6);
        List<Long> tids = new ArrayList<>();
        while (tids.size() < nt) {
            long x = pool[r.nextInt(pool.length)];
            if (!tids.contains(x)) {
                tids.add(x);
            }
        }
        ExecutionGraph.Builder b = new ExecutionGraph.Builder();
        Map<Long, Integer> len = new HashMap<>();
        List<EventKey> sends = new ArrayList<>();
        List<EventKey> recvs = new ArrayList<>();
        List<EventKey> all = new ArrayList<>();
        int n = r.nextInt(16);
        for (int i = 0; i < n; i++) {
            long tid = tids.get(r.nextInt(tids.size()));
            int idx = len.merge(tid, 1, Integer::sum) - 1;
            int kind = r.nextInt(5);
            GraphEvent e;
            if (kind < 2) {
                e = send(t(tid), idx, A, t(tids.get(r.nextInt(tids.size()))), new Msg(i));
                sends.add(new EventKey(tid, idx));
            } else if (kind < 4) {
                e = recv(t(tid), idx, A);
                recvs.add(new EventKey(tid, idx));
            } else {
                e = nondet(t(tid), idx, 2, 0);
            }
            b.add(e);
            all.add(new EventKey(tid, idx));
        }
        boolean allowCycles = r.nextInt(3) == 0;
        for (EventKey rv : recvs) {
            int c = r.nextInt(4);
            if (c == 0) {
                b.readsBottom(rv);
            } else if (!sends.isEmpty() && c > 1) {
                b.readsFrom(rv, sends.get(r.nextInt(sends.size())));
            }
        }
        int nh = all.isEmpty() ? 0 : r.nextInt(5);
        for (int i = 0; i < nh; i++) {
            EventKey x = all.get(r.nextInt(all.size()));
            EventKey y = all.get(r.nextInt(all.size()));
            // without cycles allowed, only edges forward in insertion order (still possibly with an
            // rf cycle above); with, any pair, self-loops included
            if (allowCycles || all.indexOf(x) < all.indexOf(y)) {
                b.hb(x, y);
            }
        }
        return b.build();
    }

    // ------------------------------------------------------------------ JUnit (property off in this JVM)

    @Test
    void propertyOff_inTheTestJvm() {
        assertEquals(Boolean.getBoolean("jmc.must.checkPrefixes"), ExecutionGraph.CHECK);
    }

    @Test
    void generalBuilderMatrix() {
        int cyclic = 0;
        for (Map.Entry<String, Supplier<ExecutionGraph>> e : matrix().entrySet()) {
            String v = check(e.getKey(), e.getValue().get());
            System.out.println("MATRIX " + e.getKey() + " " + v);
            cyclic += v.startsWith("cyclic") ? 1 : 0;
        }
        assertEquals(8, cyclic, "the cyclic cases of the matrix");
    }

    @Test
    void randomGraphs() {
        Random r = new Random(311);
        int cyclic = 0;
        int acyclic = 0;
        for (int i = 0; i < 4000; i++) {
            String v = check("random " + i, randomGraph(r));
            if (v.startsWith("cyclic")) {
                cyclic++;
            } else {
                acyclic++;
            }
        }
        System.out.println("RANDOM cyclic " + cyclic + " acyclic " + acyclic);
        assertTrue(cyclic > 200 && acyclic > 2000, "both kinds occur");
    }

    /**
     * Null keys (Codex round 01, m1): at 3c43b2d {@code porfBefore} looked a null {@code a} up in a
     * HashMap (absent: false) and asked a set whether it holds a null {@code b} (false). Derived: false
     * for (null, b), (a, null) and (null, null), on the empty, an acyclic and a cyclic graph, before
     * and after the first query.
     */
    @Test
    void nullKeys_areFalse_onEmptyAcyclicAndCyclicGraphs() {
        for (String name : List.of("empty", "one-event", "several-readers-of-one-send", "sparse-tids",
                "cycle-po-rf", "hb-self-loop", "cycle-plus-acyclic-component")) {
            for (boolean nullFirst : new boolean[] {true, false}) {
                ExecutionGraph g = matrix().get(name).get();
                List<EventKey> keys = new ArrayList<>();
                g.eventsInInsertionOrder().forEach(e -> keys.add(e.key()));
                keys.add(new EventKey(1, 0));
                if (!nullFirst) {
                    g.porfAcyclic();
                }
                assertFalse(g.porfBefore(null, null), name + ": (null, null)");
                for (EventKey k : keys) {
                    assertFalse(g.porfBefore(null, k), name + ": (null, " + k + ")");
                    assertFalse(g.porfBefore(k, null), name + ": (" + k + ", null)");
                }
                check(name + " after null queries", g);
            }
        }
    }

    /** -ea alone leaves the check off; the property turns it on (fresh JVMs, J3Probe). */
    @Test
    void checkIsPropertyOnly_eaAloneDoesNotEnableIt() throws Exception {
        String ea = probe(List.of("-ea"));
        assertTrue(ea.contains("CHECK false\n") && ea.contains("EA true\n") && ea.contains("DONE failed=0"), ea);
        String on = probe(List.of("-Djmc.must.checkPrefixes=true"));
        assertTrue(on.contains("CHECK true\n") && on.contains("DONE failed=0"), on);
        String both = probe(List.of("-ea", "-Djmc.must.checkPrefixes=true"));
        assertTrue(both.contains("CHECK true\n") && both.contains("DONE failed=0"), both);
        String off = probe(List.of());
        assertTrue(off.contains("CHECK false\n") && off.contains("EA false\n") && off.contains("DONE failed=0"), off);
    }

    /**
     * Codex round 03 (M4): a SecurityManager refusing only the read of {@code jmc.must.checkPrefixes},
     * installed before ExecutionGraph and Graph initialise (fresh JVM, {@link J3PropertyDeniedProbe}),
     * with the property set to true. Derived: both CHECKs are false (the read is refused, so the
     * check is off), no class fails to initialise, every graph agrees with the reference, and the
     * Must runs give the same counts as an unrestricted JVM without the property.
     */
    @Test
    void propertyReadDenied_checkOff_graphsAndMustUnchanged() throws Exception {
        String denied = runProbe(List.of("-Djava.security.manager=allow", "-Djmc.must.checkPrefixes=true"),
                J3PropertyDeniedProbe.class.getName(), "deny");
        String plain = runProbe(List.of(), J3PropertyDeniedProbe.class.getName(), "none");
        assertTrue(denied.contains("EG.CHECK false\n") && denied.contains("GRAPH.CHECK false\n"), denied);
        assertTrue(denied.contains("REFUSED reads true\n") && denied.contains("DONE failed=0"), denied);
        assertTrue(plain.contains("EG.CHECK false\n") && plain.contains("GRAPH.CHECK false\n")
                && plain.contains("DONE failed=0"), plain);
        assertEquals(lines(plain, "MUST "), lines(denied, "MUST "), "Must runs, denied versus unrestricted");
        assertEquals(lines(plain, "MATRIX "), lines(denied, "MATRIX "), "the matrix verdicts");
        assertTrue(lines(denied, "MUST ").size() == 5 && !denied.contains("failed:"), denied);
        assertTrue(denied.contains("GRAPHS checked " + (matrix().size() + 1000) + "\n"), denied);
    }

    static List<String> lines(String text, String prefix) {
        List<String> out = new ArrayList<>();
        for (String l : text.split("\n")) {
            if (l.startsWith(prefix)) {
                out.add(l);
            }
        }
        return out;
    }

    static String runProbe(List<String> jvm, String main, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.addAll(jvm);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(main);
        cmd.addAll(List.of(args));
        Path log = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "j3m4-", ".txt");
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean done = p.waitFor(600, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
        }
        String text = Files.readString(log, StandardCharsets.UTF_8);
        StringBuilder b = new StringBuilder();
        for (String l : text.split("\n")) {
            if (l.matches("^(SM|EG\\.CHECK|GRAPH\\.CHECK|MATRIX|GRAPHS|MUST|FAIL|REFUSED|DONE) .*")) {
                b.append(l).append('\n');
            }
        }
        System.out.println("PROBE " + jvm + " " + List.of(args) + "\n" + b);
        assertTrue(done, "probe finished");
        return b.toString();
    }

    static String probe(List<String> jvm) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.addAll(jvm);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(J3Probe.class.getName());
        Path log = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "j3probe-", ".txt");
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean done = p.waitFor(600, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
        }
        String text = Files.readString(log, StandardCharsets.UTF_8);
        StringBuilder b = new StringBuilder();
        for (String l : text.split("\n")) {
            if (l.startsWith("CHECK ") || l.startsWith("EA ") || l.startsWith("FAIL ") || l.startsWith("DONE ")) {
                b.append(l).append('\n');
            }
        }
        System.out.println("PROBE " + jvm + "\n" + b);
        assertTrue(done, "probe finished");
        return b.toString();
    }
}
