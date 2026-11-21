package org.mpi_sws.jmc.mp.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.GraphTestSupport.nondet;
import static org.mpi_sws.jmc.mp.GraphTestSupport.recv;
import static org.mpi_sws.jmc.mp.GraphTestSupport.send;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.Tid;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMC-SPEED-2 tester round 02 (the P2 fix, sparse clocks above {@code DENSE_THREADS} = 16 threads).
 * Item 1: the reviewer's input and two more bounded inputs in fresh JVMs with {@code -Xmx128m}
 * ({@link P2HeapProbe}). Item 2: random graphs with T = 16, 17 and more threads (po, rf with several
 * readers of one send, hb across threads, acyclic and cyclic) against the reference closure of
 * {@link J3PorfTest#reference} on every pair, absent and null keys; run also with
 * {@code -Djmc.must.checkPrefixes=true} (then {@code checkPorf} meets every pair too). Item 3: the
 * representation switches exactly above 16 threads (derived: T = 16 dense, T = 17 sparse).
 */
public class P2SparseTest {

    static final CommunicationModel A = CommunicationModel.ASYN;

    static String heapProbe(String what) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-Xmx128m");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(P2HeapProbe.class.getName());
        cmd.add(what);
        Path log = Files.createTempFile(Path.of(System.getProperty("java.io.tmpdir")), "p2heap-", ".txt");
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean done = p.waitFor(300, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
        }
        String text = Files.readString(log, StandardCharsets.UTF_8);
        System.out.println("HEAPPROBE " + what + " rc=" + (done ? p.exitValue() : "timeout") + "\n" + text);
        assertTrue(done, "probe finished");
        return text;
    }

    @Test
    void boundedHeap_reviewerInput_10000IndependentEvents() throws Exception {
        String t = heapProbe("indep");
        assertTrue(t.contains("DONE ok") && !t.contains("OutOfMemoryError") && t.contains("maxMemory 128 MB"), t);
    }

    @Test
    void boundedHeap_4000Threads_40RfEdges() throws Exception {
        String t = heapProbe("few");
        assertTrue(t.contains("DONE ok") && !t.contains("OutOfMemoryError") && t.contains("maxMemory 128 MB"), t);
    }

    @Test
    void boundedHeap_chainOfSendsAcross2000Threads() throws Exception {
        String t = heapProbe("chain");
        assertTrue(t.contains("DONE ok") && !t.contains("OutOfMemoryError") && t.contains("maxMemory 128 MB"), t);
    }

    /** A random graph on exactly {@code nt} threads (each has at least one event). */
    static ExecutionGraph randomGraph(Random r, int nt, int extra, boolean allowCycles) {
        List<Long> tids = new ArrayList<>();
        while (tids.size() < nt) {
            long x = r.nextInt(4) == 0 ? r.nextLong() : r.nextInt(1000) - 100;
            if (!tids.contains(x)) {
                tids.add(x);
            }
        }
        ExecutionGraph.Builder b = new ExecutionGraph.Builder();
        Map<Long, Integer> len = new HashMap<>();
        List<EventKey> sends = new ArrayList<>();
        List<EventKey> recvs = new ArrayList<>();
        List<EventKey> all = new ArrayList<>();
        List<Long> who = new ArrayList<>(tids);
        for (int i = 0; i < extra; i++) {
            who.add(tids.get(r.nextInt(nt)));
        }
        java.util.Collections.shuffle(who, r);
        for (int i = 0; i < who.size(); i++) {
            long tid = who.get(i);
            int idx = len.merge(tid, 1, Integer::sum) - 1;
            int kind = r.nextInt(5);
            GraphEvent e;
            if (kind < 2) {
                e = send(Tid.of(tid), idx, A, Tid.of(tids.get(r.nextInt(nt))), new Msg(i));
                sends.add(new EventKey(tid, idx));
            } else if (kind < 4) {
                e = recv(Tid.of(tid), idx, A);
                recvs.add(new EventKey(tid, idx));
            } else {
                e = nondet(Tid.of(tid), idx, 2, 0);
            }
            b.add(e);
            all.add(new EventKey(tid, idx));
        }
        // several readers of one send: a third of the receives read the first send
        for (EventKey rv : recvs) {
            int c = r.nextInt(6);
            if (c == 0) {
                b.readsBottom(rv);
            } else if (!sends.isEmpty() && c < 3) {
                b.readsFrom(rv, sends.get(0));
            } else if (!sends.isEmpty() && c < 5) {
                b.readsFrom(rv, sends.get(r.nextInt(sends.size())));
            }
        }
        int nh = r.nextInt(2 * nt);
        for (int i = 0; i < nh; i++) {
            EventKey x = all.get(r.nextInt(all.size()));
            EventKey y = all.get(r.nextInt(all.size()));
            if (x.tid() != y.tid() && (allowCycles || all.indexOf(x) < all.indexOf(y))) {
                b.hb(x, y);
            }
        }
        if (allowCycles && r.nextBoolean()) {
            EventKey x = all.get(r.nextInt(all.size()));
            b.hb(x, x);
        }
        return b.build();
    }

    static boolean sparse(ExecutionGraph g) throws Exception {
        g.porfAcyclic();
        Field f = ExecutionGraph.class.getDeclaredField("porf");
        f.setAccessible(true);
        Object p = f.get(g);
        Method m = p.getClass().getDeclaredMethod("sparse");
        m.setAccessible(true);
        Field c = p.getClass().getDeclaredField("clocks");
        c.setAccessible(true);
        if (c.get(p) == null) {
            throw new IllegalStateException("cyclic: no clocks");
        }
        return (Boolean) m.invoke(p);
    }

    @Test
    void sparseEqualsClosure_randomGraphs_T16_T17_andMore() {
        System.out.println("CHECK " + ExecutionGraph.CHECK);
        Random r = new Random(4242);
        int[] ts = {16, 17, 17, 18, 20, 24, 33, 40};
        int cyclic = 0;
        int acyclic = 0;
        for (int i = 0; i < 400; i++) {
            int nt = ts[i % ts.length];
            ExecutionGraph g = randomGraph(r, nt, r.nextInt(3 * nt), r.nextInt(3) == 0);
            assertEquals(nt, g.threads().size());
            String v = J3PorfTest.check("p2 random " + i + " T=" + nt, g);
            if (v.startsWith("cyclic")) {
                cyclic++;
            } else {
                acyclic++;
            }
        }
        System.out.println("P2 RANDOM cyclic " + cyclic + " acyclic " + acyclic);
        assertTrue(cyclic > 40 && acyclic > 200, "both kinds occur");
    }

    @Test
    void sparsePath_nullKeys_areFalse() {
        Random r = new Random(5);
        for (int i = 0; i < 20; i++) {
            ExecutionGraph g = randomGraph(r, 17 + i, 20, false);
            List<EventKey> keys = new ArrayList<>();
            g.eventsInInsertionOrder().forEach(e -> keys.add(e.key()));
            if (i % 2 == 0) {
                g.porfAcyclic();
            }
            assertFalse(g.porfBefore(null, null));
            for (EventKey k : keys) {
                assertFalse(g.porfBefore(null, k));
                assertFalse(g.porfBefore(k, null));
            }
            J3PorfTest.check("p2 null " + i, g);
        }
    }

    @Test
    void threshold_denseUpTo16_sparseFrom17() throws Exception {
        assertEquals(16, ExecutionGraph.DENSE_THREADS);
        Random r = new Random(6);
        for (int nt = 1; nt <= 40; nt++) {
            ExecutionGraph g = randomGraph(r, nt, nt, false);
            if (!g.porfAcyclic()) {
                continue;
            }
            assertEquals(nt > 16, sparse(g), "T=" + nt);
            J3PorfTest.check("p2 threshold T=" + nt, g);
        }
    }
}
