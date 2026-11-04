package org.mpi_sws.jmc.mp.graph;

import static org.mpi_sws.jmc.mp.GraphTestSupport.nondet;
import static org.mpi_sws.jmc.mp.GraphTestSupport.recv;
import static org.mpi_sws.jmc.mp.GraphTestSupport.send;

import org.mpi_sws.jmc.mp.MpTestSupport.Msg;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * JMC-SPEED-2 tester round 02 item 1 (P2): run in a fresh JVM with {@code -Xmx128m} by
 * {@link P2SparseTest}. Inputs (argument): {@code indep} — the reviewer's 10 000 independent events,
 * each on its own thread at index 0, no rf/hb (derived: acyclic, porfBefore false for every pair);
 * {@code few} — 4 000 threads × 3 events (nondet, send, recv) and 40 rf edges from a send of thread
 * i to the recv of thread i+1 (derived by a BFS over the edges here); {@code chain} — a chain of
 * sends across 2 000 threads: thread i receives from thread i−1's send and then sends (derived:
 * (i, x) porf (j, y) iff i &lt; j, or i = j and x &lt; y). Prints {@code DONE ok} or {@code FAIL ...}.
 */
public final class P2HeapProbe {

    static final CommunicationModel A = CommunicationModel.ASYN;

    public static void main(String[] args) {
        String what = args[0];
        long t0 = System.nanoTime();
        int bad = 0;
        long checked = 0;
        if (what.equals("indep")) {
            ExecutionGraph.Builder b = new ExecutionGraph.Builder();
            for (int i = 0; i < 10_000; i++) {
                b.add(nondet(Tid.of(i), 0, 2, 0));
            }
            ExecutionGraph g = b.build();
            if (!g.porfAcyclic()) {
                bad++;
            }
            Random r = new Random(1);
            for (int s = 0; s < 200_000; s++) {
                EventKey a = new EventKey(r.nextInt(10_000), 0);
                EventKey c = new EventKey(r.nextInt(10_000), 0);
                if (g.porfBefore(a, c)) {
                    bad++;
                    System.out.println("FAIL " + a + " porf " + c);
                }
                checked++;
            }
        } else if (what.equals("few")) {
            int nt = 4_000;
            ExecutionGraph.Builder b = new ExecutionGraph.Builder();
            Map<EventKey, List<EventKey>> succ = new HashMap<>();
            for (int i = 0; i < nt; i++) {
                b.add(nondet(Tid.of(i), 0, 2, 0));
                b.add(send(Tid.of(i), 1, A, Tid.of((i + 1) % nt), new Msg(i)));
                b.add(recv(Tid.of(i), 2, A));
                for (int x = 0; x < 2; x++) {
                    succ.computeIfAbsent(new EventKey(i, x), k -> new ArrayList<>()).add(new EventKey(i, x + 1));
                }
            }
            for (int e = 0; e < 40; e++) {
                int i = e * 97;
                b.readsFrom(new EventKey(i + 1, 2), new EventKey(i, 1));
                succ.computeIfAbsent(new EventKey(i, 1), k -> new ArrayList<>()).add(new EventKey(i + 1, 2));
            }
            ExecutionGraph g = b.build();
            if (!g.porfAcyclic()) {
                bad++;
            }
            Random r = new Random(2);
            for (int s = 0; s < 300; s++) {
                EventKey a = new EventKey(s < 40 ? s * 97 : r.nextInt(nt), r.nextInt(3));
                Set<EventKey> reach = new HashSet<>();
                Deque<EventKey> todo = new ArrayDeque<>(succ.getOrDefault(a, List.of()));
                while (!todo.isEmpty()) {
                    EventKey k = todo.pop();
                    if (reach.add(k)) {
                        todo.addAll(succ.getOrDefault(k, List.of()));
                    }
                }
                for (int q = 0; q < 200; q++) {
                    EventKey c = q < 3 ? new EventKey(a.tid() + 1, q) : new EventKey(r.nextInt(nt), r.nextInt(3));
                    if (g.porfBefore(a, c) != reach.contains(c)) {
                        bad++;
                        System.out.println("FAIL " + a + " porf " + c + " expected " + reach.contains(c));
                    }
                    checked++;
                }
            }
        } else if (what.equals("chain")) {
            int nt = 2_000;
            ExecutionGraph.Builder b = new ExecutionGraph.Builder();
            for (int i = 0; i < nt; i++) {
                b.add(recv(Tid.of(i), 0, A));
                b.add(send(Tid.of(i), 1, A, Tid.of(i + 1), new Msg(i)));
            }
            for (int i = 1; i < nt; i++) {
                b.readsFrom(new EventKey(i, 0), new EventKey(i - 1, 1));
            }
            b.readsBottom(new EventKey(0, 0));
            ExecutionGraph g = b.build();
            if (!g.porfAcyclic()) {
                bad++;
            }
            Random r = new Random(3);
            for (int s = 0; s < 200_000; s++) {
                int i = r.nextInt(nt);
                int j = s % 4 == 0 ? i : r.nextInt(nt);
                int x = r.nextInt(2);
                int y = r.nextInt(2);
                boolean exp = i < j || (i == j && x < y);
                if (g.porfBefore(new EventKey(i, x), new EventKey(j, y)) != exp) {
                    bad++;
                    System.out.println("FAIL (" + i + "," + x + ") porf (" + j + "," + y + ") expected " + exp);
                }
                checked++;
            }
        }
        System.out.println("CHECKED " + what + " " + checked + " in " + (System.nanoTime() - t0) / 1_000_000 + " ms, maxMemory "
                + Runtime.getRuntime().maxMemory() / (1 << 20) + " MB");
        System.out.println(bad == 0 && checked > 0 ? "DONE ok" : "DONE bad=" + bad);
    }
}
