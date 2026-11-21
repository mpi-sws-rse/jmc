package org.mpi_sws.jmc.mp.j8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SPLIT tester round 02 (S-a1), gate sa1-01: an online miss after iteration 1 where the completion order differs
 * from the merge order. OnlineBank:2 at K 1: root {@code [1]}, item 2 {@code [2,3]}, item 1 {@code [4..6]}, item 0
 * {@code [7..12]}. The specification lacks the lines of iterations 5 and 8 (misses in items 1 and 0 only).
 * With W 1 the default order completes item 0 (miss at 8) before item 1 (miss at 5); the seam completes item 2,
 * then 1, then 0. Derived for every order and W 1, 2, 3, 4: complete; first miss at sequential iteration 5,
 * witness = the export line of iteration 5, answer NoSig, 2 uncovered graphs, 2 distinct, lookups 12 (10 found,
 * 2 without signature). With W 1 the commit order is checked from the result files' modification times
 * (default: item 0 before item 1; seam: item 1 before item 0); per item, the online objects give uncovered
 * 1, 1, 0 for items 0, 1, 2 and first_local 2 (iteration 8 is the 2nd of item 0) and 2 (iteration 5 is the 2nd of
 * item 1).
 */
class J8SOrderOnlineTest {

    @Test
    void missAfterIteration1_completionOrderDiffersFromMerge_sameOnlineFields() throws Exception {
        Path spec = J8SSupport.bankSpec("order-5-8", 5, 8);
        String witness = J8SSupport.bankLine(5);
        List<String> bad = new ArrayList<>();
        for (int w : new int[] {1, 2, 3, 4}) {
            for (boolean rev : new boolean[] {false, true}) {
                String tag = UUID.randomUUID().toString();
                Path d = J8SApiTest.dir("order-online-w" + w + (rev ? "-seam" : ""));
                SplitCoordinator.Result r = J8SApiTest.run(d, spec, 1, w, rev,
                        (x, id) -> J8SApiTest.realWorker(x, id, J8SSupport.BANK, tag));
                SplitCoordinator.Online on = r.online();
                String label = "W " + w + (rev ? " seam" : "");
                System.out.println("ORDERONLINE " + label + ": failure " + r.failure() + " items " + r.itemIterations()
                        + " online " + (on == null ? null : on.miss() + " " + on.firstMissIteration() + " "
                        + on.answer() + " " + on.uncoveredTotal() + "/" + on.uncoveredDistinct() + " lookups "
                        + on.lookups() + " found " + on.found() + " noSig " + on.noSig()));
                assertNull(r.failure(), label);
                assertNotNull(on, label);
                assertEquals("{0=6, 1=3, 2=2}", r.itemIterations().toString(), label);
                assertTrue(on.miss(), label);
                assertEquals(5, on.firstMissIteration(), label);
                assertEquals(witness, on.witness(), label);
                assertEquals("NoSig", on.answer(), label);
                assertEquals(2L, on.uncoveredTotal(), label);
                assertEquals(2, on.uncoveredDistinct(), label);
                assertEquals(12L, on.lookups(), label);
                assertEquals(10L, on.found(), label);
                assertEquals(2L, on.noSig(), label);
                long[][] perItem = {{0, 1, 2}, {1, 1, 2}, {2, 0, 0}};
                for (long[] e : perItem) {
                    var o = J8SApiTest.env(d, (int) e[0]).getAsJsonObject("online");
                    assertEquals(e[1], o.get("uncovered").getAsLong(), label + " item " + e[0] + " " + o);
                    assertEquals(e[2], o.get("first_local").getAsLong(), label + " item " + e[0] + " " + o);
                }
                if (w == 1) {
                    long t0 = Files.getLastModifiedTime(d.resolve("item-0.json")).toMillis();
                    long t1 = Files.getLastModifiedTime(d.resolve("item-1.json")).toMillis();
                    if (rev ? !(t1 < t0) : !(t0 < t1)) {
                        bad.add(label + ": commit order item-0 " + t0 + ", item-1 " + t1);
                    }
                }
            }
        }
        assertEquals(List.of(), bad);
    }

    /**
     * Gate sa1-02, the permitted difference (plan §5 "S-a1 as built"): the same abort injection in both orders.
     * OnlineBank:2, K 1, W 1, the specification lacks iteration 2 (item 2, first_local 1), {@code fail:0:0}.
     * Derived: old order (the seam, items 2, 1, 0): items 2 and 1 are committed before item 0 fails, so the
     * committed miss at iteration 2 is reported (first 2, witness = iteration 2's line); default order
     * (0, 1, 2): item 0 is the first assignment and fails at once: nothing committed, no miss reported. Both
     * abort with item 0's fail marker; no worker is left.
     */
    @Test
    void sameAbortInjection_bothOrders_permittedDifference() throws Exception {
        Path spec = J8SSupport.bankSpec("order-abort-drop2", 2);
        for (boolean rev : new boolean[] {true, false}) {
            String tag = UUID.randomUUID().toString();
            Path d = J8SApiTest.dir("order-abort" + (rev ? "-seam" : ""));
            SplitCoordinator.Result r = J8SApiTest.run(d, spec, 1, 1, rev, (x, id) -> J8SApiTest.realWorker(x, id,
                    J8SSupport.BANK, tag, List.of("-Djmc.split.fault=fail:0:0")));
            List<Integer> committed = J8SAbortOutputTest.committed(d);
            SplitCoordinator.Online on = r.online();
            String label = rev ? "old order (seam)" : "default order";
            System.out.println("ORDERABORT " + label + ": failure " + r.failure() + " committed " + committed
                    + " online " + (on == null ? null : on.miss() + " first " + on.firstMissIteration()) + " leftovers "
                    + org.mpi_sws.jmc.mp.j8.J8LeftoversView.leftovers(tag));
            assertNotNull(r.failure(), label);
            assertTrue(r.failure().contains("worker-0.fail.json") || r.failure().contains("item 0"), label + ": " + r.failure());
            assertNotNull(on, label);
            if (rev) {
                assertEquals(List.of(1, 2), committed, label);
                assertTrue(on.miss(), label);
                assertEquals(2, on.firstMissIteration(), label);
                assertEquals(J8SSupport.bankLine(2), on.witness(), label);
            } else {
                assertEquals(List.of(), committed, label);
                assertTrue(!on.miss(), label);
            }
            assertEquals(List.of(), org.mpi_sws.jmc.mp.j8.J8LeftoversView.leftovers(tag), label + ": no worker left");
        }
    }
}
