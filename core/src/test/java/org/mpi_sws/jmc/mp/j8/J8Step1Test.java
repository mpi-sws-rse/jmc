package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.strategies.must.MustStrategy;
import org.mpi_sws.jmc.strategies.must.split.ItemResult;
import org.mpi_sws.jmc.strategies.must.split.StackFingerprint;
import org.mpi_sws.jmc.strategies.must.split.TypedGraphEncoding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;

/**
 * J8 step 1 (processes), tester round 04 (plan/jmc-must/log/dev/J8/round-04.prompt.md; J8-PLAN revision
 * 8 §5, §7 tests 2, 3, 6, 7, 8, 9 and the measurement block). Expected values: the tester's round-01
 * derivation (segments, sequential export md5s) and the sequential launcher run of the same row.
 * Every launch is a fresh {@code MpLauncher} JVM ({@link J8Launch}); fault injection through
 * {@code -Djmc.split.fault} (rev 7 D5), which the coordinator passes to its workers with its own JVM options.
 */
class J8Step1Test {

    static final String A3FIFO = J8Launch.J4 + "A3Lock:fifo,3,1";
    /** Round-01 derivation: md5 of the sequential export of A3Lock:fifo,3,1 (R 1, N 5, items 2 4 3 10 10). */
    static final String A3FIFO_MD5 = "ddc264d82b01cdb97a8860dd701394b2";

    static void complete(J8Launch.Out o, String graphs) {
        assertEquals(0, o.exit(), o.text());
        assertEquals(graphs, o.field("graphs"), o.text());
    }

    static void aborted(J8Launch.Out o, String reasonPart) {
        assertEquals(1, o.exit(), o.text());
        String f = o.field("FAILED");
        assertNotNull(f, o.text());
        assertTrue(f.startsWith("incomplete: ") && f.contains(reasonPart), "FAILED: " + f + "\n" + o.text());
        assertTrue(o.exportFile() == null || !Files.exists(o.exportFile()), "no export on abort");
    }

    // ---------------------------------------------------------------- test 2

    /** W = N and W = 16 at K = 1: export (J4) or counts (H1) equal to sequential; completion order recorded. */
    @Test
    void t2_wEqualsN_and_w16_equalSequential() throws Exception {
        String[][] rows = {
            // row, export?, N at K=1 (round-01), sequential graphs line
            {A3FIFO, "true", "5", "30 distinct complete, 0 distinct blocked; stack exhausted"},
            {J8Launch.J4 + "A3Lock:double-grant,3,1", "true", "9", "90 distinct complete, 0 distinct blocked; stack exhausted"},
            {J8Launch.J4 + "A3Lock:never-grant,3,1", "true", "5", "18 distinct complete, 10 distinct blocked; stack exhausted"},
            {J8Launch.J4 + "A2Store:pb,3,1", "true", "3", "6 distinct complete, 0 distinct blocked; stack exhausted"},
            {J8Launch.H1 + "RuPaxos:1", "false", "23", "1299 distinct complete, 201 distinct blocked; stack exhausted"},
            {J8Launch.H1 + "JobQueue:pull,4", "false", "12", "256 distinct complete, 0 distinct blocked; stack exhausted"},
        };
        List<String> evidence = new ArrayList<>();
        for (String[] r : rows) {
            boolean export = Boolean.parseBoolean(r[1]);
            J8Launch.Out seq = J8Launch.launch(r[0], export);
            complete(seq, r[3]);
            for (String w : List.of(r[2], "16")) {
                J8Launch.Out o = J8Launch.launch(r[0], export, "--split", w, "--k", "1");
                complete(o, r[3]);
                assertEquals(seq.field("ran"), o.field("ran"));
                assertEquals(seq.field("blocked"), o.field("blocked"));
                assertTrue(o.field("split").startsWith("K 1, W " + w + ", R 1, N " + r[2] + ";"), o.field("split"));
                if (export) {
                    assertEquals(seq.exportMd5(), o.exportMd5(), r[0] + " W=" + w + ": export byte-identical");
                }
                List<Integer> order = J8Launch.completionOrder(o.runDir());
                evidence.add(r[0] + " W=" + w + " items " + o.field("items") + " completion order " + order
                        + (export ? " export md5 " + o.exportMd5() : ""));
            }
        }
        J8Launch.Out a3 = J8Launch.launch(A3FIFO, true);
        assertEquals(A3FIFO_MD5, a3.exportMd5(), "sequential export equals the round-01 md5");
        evidence.forEach(e -> System.out.println("J8R4 t2 " + e));
    }

    // ---------------------------------------------------------------- test 3

    static String runJsonFingerprint(J8Launch.Out o) throws Exception {
        String json = Files.readString(o.runDir().resolve("run.json"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"fingerprint\":\"([0-9a-f]{64})\"").matcher(json);
        assertTrue(m.find(), json);
        return m.group(1);
    }

    @Test
    void t3_fingerprintEqualAcrossJvms_hashCodeAndHeap() throws Exception {
        List<String> a = List.of("-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=2", "-Xmx128m");
        List<String> b = List.of("-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=5", "-Xmx1g");
        for (String[] r : new String[][] {{J8Launch.J8 + "EnumNested", "false", "4 distinct complete, 0 distinct blocked; stack exhausted"},
            {A3FIFO, "true", "30 distinct complete, 0 distinct blocked; stack exhausted"}}) {
            boolean export = Boolean.parseBoolean(r[1]);
            J8Launch.Out x = J8Launch.launch(a, r[0], export, "--split", "2", "--k", "1");
            J8Launch.Out y = J8Launch.launch(b, r[0], export, "--split", "2", "--k", "1");
            complete(x, r[2]);
            complete(y, r[2]);
            String fx = runJsonFingerprint(x);
            String fy = runJsonFingerprint(y);
            System.out.println("J8R4 t3 " + r[0] + " fingerprint A " + fx + " B " + fy);
            assertEquals(fx, fy);
            if (export) {
                assertEquals(x.exportMd5(), y.exportMd5());
                assertEquals(A3FIFO_MD5, x.exportMd5());
            }
        }
    }

    /** Captures the root's state at the boundary, in this JVM. */
    static final class Capture implements MustStrategy.SplitControl {
        final List<ItemResult.Outcome> transcript = new ArrayList<>();
        MustStrategy.SplitView view;

        @Override
        public int splitThreshold() {
            return 1;
        }

        @Override
        public void outcome(boolean blocked, GraphCanon canon, LongFunction<String> names) {
            transcript.add(new ItemResult.Outcome(blocked,
                    TypedGraphEncoding.sha256(TypedGraphEncoding.outcome(canon, names))));
        }

        @Override
        public boolean atBoundary(MustStrategy.SplitView v) {
            view = v;
            return false;
        }

        @Override
        public void segmentDone(MustStrategy.Segment s) {}

        @Override
        public OptionalInt nextItem() {
            return OptionalInt.empty();
        }
    }

    static MustStrategy.EntryView withG(MustStrategy.EntryView e, MustStrategy.GraphView g) {
        return new MustStrategy.EntryView(e.variant(), g, e.task(), e.label(), e.send(), e.optionCount(), e.chosen(), e.hbIn());
    }

    static MustStrategy.SplitView withEntry(MustStrategy.SplitView v, int i, MustStrategy.EntryView e) {
        List<MustStrategy.EntryView> l = new ArrayList<>(v.stackTopFirst());
        l.set(i, e);
        return new MustStrategy.SplitView(v.finishedIterations(), l, v.names(), v.nextId());
    }

    static MustStrategy.SplitView withNode(MustStrategy.SplitView v, int entry, int node,
                                           UnaryOperator<MustStrategy.NodeView> f) {
        MustStrategy.EntryView e = v.stackTopFirst().get(entry);
        List<MustStrategy.NodeView> nodes = new ArrayList<>(e.g().nodesByStamp());
        nodes.set(node, f.apply(nodes.get(node)));
        return withEntry(v, entry, withG(e, new MustStrategy.GraphView(nodes, e.g().nextStamp())));
    }

    @Test
    void t3_fingerprintMutants_eachChangesTheFingerprint() throws Exception {
        Capture c = new Capture();
        MustStrategy s = new MustStrategy(1L, "build/test-results/jmc-report");
        s.setSplitControl(c);
        EnumNested program = new EnumNested();
        new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType("must").numIterations(0).seed(1L)
                .reportPath("build/test-results/jmc-report").strategyConstructor(x -> s)
                .timeout(Duration.ofSeconds(120)).build())
                .check(new JmcFunctionalTestTarget("EnumNested", () -> MpProcess.runMain(program)));
        MustStrategy.SplitView v = c.view;
        assertNotNull(v);
        String id = "org.mpi_sws.jmc.mp.j8.EnumNested";
        String fp = StackFingerprint.of(id, 1, v, c.transcript);
        assertEquals(fp, StackFingerprint.of(id, 1, v, new ArrayList<>(c.transcript)), "recomputed equal");
        // round-01 derivation: R 1, N 2, top = Forward (recv of r reading b's send), bottom = Nondet chosen 1
        assertEquals(1, v.finishedIterations());
        assertEquals(2, v.stackTopFirst().size());
        MustStrategy.EntryView fwd = v.stackTopFirst().get(0);
        MustStrategy.EntryView nd = v.stackTopFirst().get(1);
        assertEquals("Forward", fwd.variant());
        assertEquals("Nondet", nd.variant());
        int sendNode = -1;
        for (int i = 0; i < fwd.g().nodesByStamp().size(); i++) {
            if (fwd.g().nodesByStamp().get(i).label() instanceof MpLabel.Send) {
                sendNode = i;
            }
        }
        assertTrue(sendNode >= 0, "a send in the Forward entry's graph");
        MpLabel.Recv rl = (MpLabel.Recv) fwd.label();
        List<String[]> results = new ArrayList<>();
        java.util.Map<String, MustStrategy.SplitView> views = new java.util.LinkedHashMap<>();
        views.put("stamp", withNode(v, 0, 0, n -> new MustStrategy.NodeView(n.key(), n.label(), n.stamp() + 100, n.rf(),
                n.bottom(), n.hbIn(), n.prefix())));
        views.put("hb edge (node)", withNode(v, 0, sendNode, n -> {
            TreeSet<EventKey> hb = new TreeSet<>(n.hbIn());
            hb.add(new EventKey(1, 0));
            return new MustStrategy.NodeView(n.key(), n.label(), n.stamp(), n.rf(), n.bottom(), hb, n.prefix());
        }));
        TreeSet<EventKey> entryHb = new TreeSet<>(fwd.hbIn());
        entryHb.add(new EventKey(1, 0));
        views.put("hb edge (entry)", withEntry(v, 0, new MustStrategy.EntryView(fwd.variant(), fwd.g(), fwd.task(),
                fwd.label(), fwd.send(), fwd.optionCount(), fwd.chosen(), entryHb)));
        SortedMap<String, Long> names = new TreeMap<>(v.names());
        String some = names.lastKey();
        Long someId = names.remove(some);
        names.put(some + "x", someId);
        views.put("name", new MustStrategy.SplitView(v.finishedIterations(), v.stackTopFirst(), names, v.nextId()));
        views.put("next id", new MustStrategy.SplitView(v.finishedIterations(), v.stackTopFirst(), v.names(), v.nextId() + 1));
        views.put("nondet value", withEntry(v, 1, new MustStrategy.EntryView(nd.variant(), nd.g(), nd.task(), nd.label(),
                nd.send(), nd.optionCount(), 0, nd.hbIn())));
        views.put("Forward label (blocking)", withEntry(v, 0, new MustStrategy.EntryView(fwd.variant(), fwd.g(), fwd.task(),
                new MpLabel.Recv(rl.model(), !rl.blocking()), fwd.send(), -1, -1, fwd.hbIn())));
        views.put("Recv.model (Forward label)", withEntry(v, 0, new MustStrategy.EntryView(fwd.variant(), fwd.g(), fwd.task(),
                new MpLabel.Recv(CommunicationModel.ASYN, rl.blocking()), fwd.send(), -1, -1, fwd.hbIn())));
        views.put("Forward send", withEntry(v, 0, new MustStrategy.EntryView(fwd.variant(), fwd.g(), fwd.task(),
                fwd.label(), null, -1, -1, fwd.hbIn())));
        final int sn = sendNode;
        views.put("Send.model (graph node)", withNode(v, 0, sn, n -> {
            MpLabel.Send sl = (MpLabel.Send) n.label();
            return new MustStrategy.NodeView(n.key(), new MpLabel.Send(CommunicationModel.ASYN, sl.dst(), sl.value()),
                    n.stamp(), n.rf(), n.bottom(), n.hbIn(), n.prefix());
        }));
        views.put("next stamp", withEntry(v, 0, withG(fwd, new MustStrategy.GraphView(fwd.g().nodesByStamp(),
                fwd.g().nextStamp() + 1))));
        views.put("R", new MustStrategy.SplitView(v.finishedIterations() + 1, v.stackTopFirst(), v.names(), v.nextId()));
        for (java.util.Map.Entry<String, MustStrategy.SplitView> e : views.entrySet()) {
            String m = StackFingerprint.of(id, 1, e.getValue(), c.transcript);
            results.add(new String[] {e.getKey(), m});
            assertNotEquals(fp, m, "mutant " + e.getKey());
        }
        List<ItemResult.Outcome> t1 = new ArrayList<>(c.transcript);
        t1.set(0, new ItemResult.Outcome(!t1.get(0).blocked(), t1.get(0).digest()));
        assertNotEquals(fp, StackFingerprint.of(id, 1, v, t1), "transcript tag");
        List<ItemResult.Outcome> t2 = new ArrayList<>(c.transcript);
        t2.set(0, new ItemResult.Outcome(t2.get(0).blocked(), "0" + t2.get(0).digest().substring(1)));
        assertNotEquals(fp, StackFingerprint.of(id, 1, v, t2), "transcript digest");
        assertNotEquals(fp, StackFingerprint.of(id, 2, v, c.transcript), "K");
        assertNotEquals(fp, StackFingerprint.of(id + "x", 1, v, c.transcript), "program identity");
        System.out.println("J8R4 t3 fingerprint " + fp + "; " + results.size() + " view mutants + 4 others all differ");
    }

    // ---------------------------------------------------------------- tests 6, 9

    static J8Launch.Out faulted(String fault, int w) throws Exception {
        J8Launch.Out o = J8Launch.launch(List.of("-Djmc.split.fault=" + fault), A3FIFO, true, "--split",
                Integer.toString(w), "--k", "1");
        System.out.println("J8R4 fault " + fault + " W=" + w + "\n" + o.text());
        return o;
    }

    @Test
    void t6_dieBefore_retriedOnce_equalSequential_W1() throws Exception {
        J8Launch.Out o = faulted("die-before:3:0", 1);
        complete(o, "30 distinct complete, 0 distinct blocked; stack exhausted");
        assertEquals(A3FIFO_MD5, o.exportMd5());
        assertTrue(Files.exists(o.runDir().resolve("assigned").resolve("3.1.json")), "attempt 1 assigned");
    }

    @Test
    void t6_dieBefore_retriedOnce_equalSequential_W2() throws Exception {
        J8Launch.Out o = faulted("die-before:3:0", 2);
        complete(o, "30 distinct complete, 0 distinct blocked; stack exhausted");
        assertEquals(A3FIFO_MD5, o.exportMd5());
    }

    @Test
    void t6_dieAfter_notRetried_workerRestarted_equalSequential() throws Exception {
        for (int w : new int[] {1, 2}) {
            J8Launch.Out o = faulted("die-after:3:0", w);
            complete(o, "30 distinct complete, 0 distinct blocked; stack exhausted");
            assertEquals(A3FIFO_MD5, o.exportMd5());
            assertFalse(Files.exists(o.runDir().resolve("assigned").resolve("3.1.json")), "not retried");
        }
    }

    @Test
    void t6_itemFailMarker_aborts() throws Exception {
        J8Launch.Out o = faulted("fail:2:0", 2);
        aborted(o, "fail marker fail-2.0.json");
    }

    @Test
    void t6_fingerprintMismatch_abortsWithoutRetry() throws Exception {
        J8Launch.Out o = faulted("fingerprint:-1:-1", 2);
        aborted(o, ".fail.json");
        assertTrue(o.field("FAILED").contains("replay did not reach the coordinator's state"), o.text());
        try (var f = Files.list(o.runDir().resolve("assigned"))) {
            assertTrue(f.noneMatch(p -> p.getFileName().toString().endsWith(".1.json")), "no retry");
        }
    }

    @Test
    void t9_secondDeathOfTheSameItem_aborts() throws Exception {
        J8Launch.Out o = faulted("die-before:3:0,die-before:3:1", 2);
        aborted(o, "item 3 lost its worker twice");
    }

    @Test
    void t9_wrongEnvelope_item_attempt_worker_semanticFailure() throws Exception {
        for (String kind : List.of("wrong-item", "wrong-attempt", "wrong-worker")) {
            J8Launch.Out o = faulted(kind + ":2:0", 2);
            aborted(o, "does not match the assignment");
            assertFalse(Files.exists(o.runDir().resolve("assigned").resolve("2.1.json")), kind + ": not retried");
        }
    }

    // ---------------------------------------------------------------- test 7

    @Test
    void t7_processNamedAfterTheSplit_refused() throws Exception {
        J8Launch.Out o = J8Launch.launch(J8Launch.J8 + "LateName", false, "--split", "1", "--k", "1");
        aborted(o, "first created after the split boundary");
        J8Launch.Out o2 = J8Launch.launch(J8Launch.J8 + "LateName", false, "--split", "1", "--k", "2");
        complete(o2, "2 distinct complete, 0 distinct blocked; stack exhausted");
        assertTrue(o2.field("split").contains("no split"), o2.text());
    }

    @Test
    void t7_specWithSplit_exit2() throws Exception {
        J8Launch.Out spec = J8Launch.launch(J8Launch.J4 + "A3Lock:spec,2,1", true);
        assertEquals(0, spec.exit(), spec.text());
        J8Launch.Out o = J8Launch.launch(J8Launch.J4 + "A3Lock:fifo,2,1", false, "--spec",
                spec.exportFile().toString(), "--split", "2");
        System.out.println("J8R4 t7 spec+split exit " + o.exit() + "\n" + o.text());
        assertEquals(2, o.exit(), o.text());
        assertTrue(o.text().contains("--spec cannot be combined with --split"), o.text());
        // the spec itself is accepted without --split
        J8Launch.Out ok = J8Launch.launch(J8Launch.J4 + "A3Lock:fifo,2,1", false, "--spec", spec.exportFile().toString());
        assertEquals(0, ok.exit(), ok.text());
    }

    @Test
    void t7_valueOutsideContract_refused_sequentialAccepts() throws Exception {
        for (String kind : List.of("set", "double", "array")) {
            String p = J8Launch.J8 + "OutsidePayload:" + kind;
            J8Launch.Out seq = J8Launch.launch(p, false);
            complete(seq, "2 distinct complete, 0 distinct blocked; stack exhausted");
            J8Launch.Out o = J8Launch.launch(p, false, "--split", "1", "--k", "1");
            System.out.println("J8R4 t7 outside " + kind + ": " + o.field("FAILED"));
            aborted(o, "split mode refuses a payload value");
        }
    }

    // ---------------------------------------------------------------- test 8

    @Test
    void t8_cleanupIndependence_acrossItemsInWorkers() throws Exception {
        String graphs = "4 distinct complete, 2 distinct blocked; stack exhausted";
        for (String w : List.of("1", "3")) {
            long t0 = System.nanoTime();
            J8Launch.Out fast = J8Launch.launch(J8Launch.J8 + "CleanupDelay:0", false, "--split", w, "--k", "1");
            long t1 = System.nanoTime();
            J8Launch.Out slow = J8Launch.launch(J8Launch.J8 + "CleanupDelay:300", false, "--split", w, "--k", "1");
            long t2 = System.nanoTime();
            complete(fast, graphs);
            complete(slow, graphs);
            assertEquals("6 iterations", slow.field("ran"));
            assertEquals("2 (counted by the strategy)", slow.field("blocked"));
            assertEquals("{0=2, 1=2, 2=1}", slow.field("items"));
            assertEquals(fast.field("items"), slow.field("items"));
            long fastMs = (t1 - t0) / 1_000_000;
            long slowMs = (t2 - t1) / 1_000_000;
            System.out.println("J8R4 t8 W=" + w + ": delay 0 run " + fastMs + " ms, delay 300 run " + slowMs + " ms");
            if (w.equals("1")) {
                // one worker explores all items: the stale waits fall between the root replay and item 2,
                // and between item 2 (blocked) and item 1, in that worker
                assertTrue(slowMs - fastMs >= 400, fastMs + " vs " + slowMs);
            }
        }
    }

    // ---------------------------------------------------------------- measurement block

    @Test
    void measurements_keysOnceInOrder_countsEqualSequential() throws Exception {
        for (String[] r : new String[][] {{J8Launch.J4 + "A3Lock:never-grant,3,1", "true"},
            {J8Launch.H1 + "Actors:token_server", "false"}, {J8Launch.H1 + "Actors:bank", "false"}}) {
            boolean export = Boolean.parseBoolean(r[1]);
            J8Launch.Out seq = J8Launch.launch(r[0], export);
            for (String[] opts : new String[][] {{"--split", "2", "--k", "1"}, {"--split", "2", "--k", "500"}}) {
                J8Launch.Out o = J8Launch.launch(r[0], export, opts);
                assertEquals(0, o.exit(), o.text());
                List<String> keys = o.block().stream().map(x -> x[0]).toList();
                List<String> seqKeys = seq.block().stream().map(x -> x[0]).toList();
                System.out.println("J8R4 meas " + r[0] + " " + String.join(" ", opts) + " keys " + keys);
                assertEquals(seqKeys, keys, "same keys in the same order as sequential");
                assertEquals(keys.size(), new TreeSet<>(keys).size(), "each key once");
                java.util.Map<String, String> sv = new java.util.HashMap<>();
                seq.block().forEach(x -> sv.put(x[0], x[1]));
                for (String[] kv : o.block()) {
                    if (List.of("graphs_complete", "graphs_blocked", "summaries_distinct", "iterations", "stop_reason")
                            .contains(kv[0])) {
                        assertEquals(sv.get(kv[0]), kv[1], kv[0]);
                    }
                }
                assertEquals(1, o.text().split("--- measurements", -1).length - 1, "one block");
            }
        }
    }
}
