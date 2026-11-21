package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.fixtures.apps.A3Lock;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * SPLIT tester round 02 (S-a1): {@code dispatch} hands items out {@code 0 … N − 1} (largest first, roughly);
 * the {@code reversedAssignment} seam selects the old {@code N − 1 … 0}; a retry goes to the queue's front;
 * the merge is unchanged. The assignment order is observed through the {@code liveness} seam: at every call,
 * the assignment files not seen before are appended (those written in one coordinator pass form one group).
 *
 * <p>Derived, A3Lock:fifo,3,1 at K 1 (N 5, items 0..4 = 10 10 3 4 2 iterations):
 * <ul>
 *   <li>W 1: {@code 0.0, 1.0, 2.0, 3.0, 4.0}, one per group; seam: {@code 4.0 … 0.0}.</li>
 *   <li>W 2 and W 3: the first group is items {@code 0 … W − 1}, item i on worker i; every later group holds
 *       larger items than every earlier group; seam: the first group {@code N − 1 … N − W}, item {@code N − 1 − i}
 *       on worker i, then decreasing.</li>
 *   <li>W 1 with {@code die-before:1:0}: {@code 0.0, 1.0, 1.1, 2.0, 3.0, 4.0} (the retry before item 2); seam
 *       with {@code die-before:3:0}: {@code 4.0, 3.0, 3.1, 2.0, 1.0, 0.0}.</li>
 *   <li>Every run, both orders, W 1, 2, 3, 5, 16: the merged results equal the base's
 *       ({@link SplitWeffTest#BASE_A3}).</li>
 * </ul>
 */
class SplitOrderTest {

    record Run(SplitCoordinator.Result r, List<List<String>> groups, Path d) {}

    static Run run(String name, int w, boolean reversed, String fault) throws Exception {
        String tag = UUID.randomUUID().toString();
        Path d = J8Round06Test.dir("order-" + name);
        List<List<String>> groups = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        A3Lock a3 = J8Round06Test.a3();
        SplitCoordinator.Result r;
        try {
            r = SplitCoordinator.run(new SplitCoordinator.Config(J8Round06Test.A3 + "|p2p", a3, a3.rolesInSpawnOrder(),
                    a3.visibleRoles(), 1, w, 300, d, (x, id) -> {
                        List<String> c = J8Round06Test.realWorker(x, id, J8Round06Test.A3, tag);
                        if (fault != null) {
                            c.add(1, "-Djmc.split.fault=" + fault);
                        }
                        return c;
                    }, reversed, p -> {
                        synchronized (groups) {
                            observe(d, seen, groups);
                        }
                        return p.isAlive();
                    }));
        } finally {
            ProcessHandle.allProcesses().forEach(h -> h.info().commandLine().ifPresent(c -> {
                if (c.contains(tag) && h.isAlive()) {
                    h.destroyForcibly();
                }
            }));
        }
        observe(d, seen, groups);
        System.out.println("ORDER " + name + " W " + w + " reversed " + reversed + " fault " + fault + ": " + groups
                + " failure " + r.failure() + " | " + SplitWeffTest.merged(d, r));
        assertNull(r.failure(), name);
        assertEquals(SplitWeffTest.BASE_A3, SplitWeffTest.merged(d, r), name + ": merged as the base");
        return new Run(r, groups, d);
    }

    static void observe(Path d, Set<String> seen, List<List<String>> groups) {
        Path a = d.resolve("assigned");
        if (!Files.isDirectory(a)) {
            return;
        }
        List<String> fresh = new ArrayList<>();
        try (var s = Files.list(a)) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.matches("\\d+\\.\\d+\\.json")).sorted()
                    .forEach(n -> {
                        if (seen.add(n)) {
                            fresh.add(n.substring(0, n.length() - 5));
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (!fresh.isEmpty()) {
            groups.add(fresh);
        }
    }

    static List<String> flat(List<List<String>> g) {
        List<String> out = new ArrayList<>();
        g.forEach(out::addAll);
        return out;
    }

    static int item(String a) {
        return Integer.parseInt(a.substring(0, a.indexOf('.')));
    }

    static String worker(Path d, String a) throws Exception {
        String s = Files.readString(d.resolve("assigned").resolve(a + ".json")).replace(" ", "");
        int i = s.indexOf("\"worker\":");
        return s.substring(i + 9).replaceAll("[^0-9].*", "");
    }

    @Test
    void w1_increasing_seamDecreasing() throws Exception {
        Run a = run("w1", 1, false, null);
        assertEquals(List.of("0.0", "1.0", "2.0", "3.0", "4.0"), flat(a.groups()));
        assertTrue(a.groups().stream().allMatch(g -> g.size() == 1), a.groups().toString());
        Run b = run("w1-seam", 1, true, null);
        assertEquals(List.of("4.0", "3.0", "2.0", "1.0", "0.0"), flat(b.groups()));
    }

    @Test
    void w2_w3_firstWItemsFirst_thenIncreasing_seamMirrored() throws Exception {
        for (int w : new int[] {2, 3}) {
            for (boolean rev : new boolean[] {false, true}) {
                Run x = run("w" + w + (rev ? "-seam" : ""), w, rev, null);
                List<List<String>> g = x.groups();
                TreeSet<Integer> first = new TreeSet<>();
                for (String s : g.get(0)) {
                    first.add(item(s));
                }
                TreeSet<Integer> expect = new TreeSet<>();
                for (int i = 0; i < w; i++) {
                    expect.add(rev ? 4 - i : i);
                    String a = (rev ? 4 - i : i) + ".0";
                    assertEquals(Integer.toString(i), worker(x.d(), a), "W " + w + " rev " + rev + ": worker of " + a);
                }
                assertEquals(expect, first, "W " + w + " rev " + rev + ": the first group " + g);
                for (int i = 1; i < g.size(); i++) {
                    for (int j = 0; j < i; j++) {
                        for (String later : g.get(i)) {
                            for (String earlier : g.get(j)) {
                                assertTrue(rev ? item(later) < item(earlier) : item(later) > item(earlier),
                                        "W " + w + " rev " + rev + ": order " + g);
                            }
                        }
                    }
                }
                assertEquals(5, flat(g).size());
            }
        }
    }

    @Test
    void w1_retryAtTheQueueFront_bothOrders() throws Exception {
        Run a = run("w1-retry", 1, false, "die-before:1:0");
        assertEquals(List.of("0.0", "1.0", "1.1", "2.0", "3.0", "4.0"), flat(a.groups()));
        Run b = run("w1-retry-seam", 1, true, "die-before:3:0");
        assertEquals(List.of("4.0", "3.0", "3.1", "2.0", "1.0", "0.0"), flat(b.groups()));
    }

    @Test
    void mergeUnchanged_bothOrders_W1_2_3_5_16() throws Exception {
        for (int w : new int[] {1, 2, 3, 5, 16}) {
            for (boolean rev : new boolean[] {false, true}) {
                Run x = run("merge-w" + w + (rev ? "-seam" : ""), w, rev, null);
                assertEquals(5, flat(x.groups()).size());
            }
        }
    }

    /**
     * No split, N 1 and a zero-iteration item, both orders (gate sa1-01): H1 Actors:bank at K 1 (R 1, N 2; item 1,
     * Backward reading Withdraw before Deposit, is empty; item 0 has 2 graphs) and Litmus:r_ss at K 1 (N 1) and
     * K 100 (no split), W 1, 2, 3, count mode. Derived: the same counts and per-item iterations in both orders:
     * bank {0=2, 1=0}, complete 3, ran 3; r_ss {0=2}, ran 3; K 100: no split, no assignment.
     */
    @Test
    void noSplit_N1_zeroIterationItem_bothOrders() throws Exception {
        String[][] rows = {
            {J8Launch.H1 + "Actors:bank", "1", "{0=2, 1=0}", "3"},
            {"org.mpi_sws.jmc.mp.h1.Litmus:r_ss", "1", "{0=2}", "3"},
            {"org.mpi_sws.jmc.mp.h1.Litmus:r_ss", "100", "{}", "3"},
        };
        for (String[] row : rows) {
            for (int w : new int[] {1, 2, 3}) {
                for (boolean rev : new boolean[] {false, true}) {
                    String tag = UUID.randomUUID().toString();
                    Path d = J8Round06Test.dir("order-count-" + row[0].replaceAll(".*\\.", "") + "-k" + row[1] + "-w" + w
                            + (rev ? "-seam" : ""));
                    Runnable prog = J8Step0Probe.instantiate(row[0]);
                    SplitCoordinator.Result r;
                    try {
                        r = SplitCoordinator.run(new SplitCoordinator.Config(row[0], prog, null, null,
                                Integer.parseInt(row[1]), w, 300, d, (x, id) -> List.of(J8Round06Test.JAVA,
                                        "-Dj8.tag=" + tag, "-Xss4m", "-cp", J8Round06Test.CP,
                                        "org.mpi_sws.jmc.mp.cli.MpLauncher", row[0], "must", "0", "1",
                                        x.resolve("worker-report").toString(), "-", "300", "--worker", x.toString(),
                                        Integer.toString(id)), rev));
                    } finally {
                        ProcessHandle.allProcesses().forEach(h -> h.info().commandLine().ifPresent(c -> {
                            if (c.contains(tag) && h.isAlive()) {
                                h.destroyForcibly();
                            }
                        }));
                    }
                    String label = row[0] + " K " + row[1] + " W " + w + (rev ? " seam" : "");
                    System.out.println("ORDERCOUNT " + label + ": failure " + r.failure() + " split " + r.split() + " n "
                            + r.n() + " items " + r.itemIterations() + " ran " + r.ran() + " dc " + r.distinctComplete()
                            + " db " + r.distinctBlocked() + " bi " + r.blockedIterations());
                    assertNull(r.failure(), label);
                    assertEquals(row[2], r.itemIterations().toString(), label);
                    assertEquals(Integer.parseInt(row[3]), r.ran(), label);
                    assertEquals(!row[1].equals("100"), r.split(), label);
                }
            }
        }
    }
}
