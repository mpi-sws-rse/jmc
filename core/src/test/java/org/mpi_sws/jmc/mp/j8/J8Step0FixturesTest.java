package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * J8 test 4 (boundary) and the step-0 parts of the late-name, normalisation-failure, export-refusal
 * and cleanup-delay fixtures, tester round 02 (J8-PLAN.md revision 7). Every run is a fresh JVM
 * ({@link J8Step0Probe}); expected values are the tester's round-01 derivation (report §4, §6 and
 * {@code step0-expected.tsv}).
 */
class J8Step0FixturesTest {

    private static final String H1 = "org.mpi_sws.jmc.mp.h1.";
    private static final String J4 = "org.mpi_sws.jmc.mp.programs.j4.";
    private static final String J8 = "org.mpi_sws.jmc.mp.j8.";

    static J8Step0Test.Run split(String row, String mode, int k) throws Exception {
        J8Step0Test.Run r = J8Step0Test.probe("split", row, mode, Integer.toString(k), "SEQUENTIAL", "-");
        System.out.println("J8 split " + row + " K=" + k + "\n" + r.raw());
        return r;
    }

    static J8Step0Test.Run seq(String row, String mode) throws Exception {
        J8Step0Test.Run r = J8Step0Test.probe("seq", row, mode);
        System.out.println("J8 seq " + row + "\n" + r.raw());
        return r;
    }

    /** "index:iterations:outcome flags" per segment, e.g. {@code -1:1:c 1:0: 0:2:cc}. */
    static String shape(J8Step0Test.Run r) {
        List<String> out = new ArrayList<>();
        for (J8Step0Test.Seg s : r.segs()) {
            StringBuilder f = new StringBuilder();
            s.outcomes().forEach(o -> f.append(o.charAt(0)));
            out.add(s.index() + ":" + s.iterations() + ":" + f);
        }
        return String.join(" ", out);
    }

    static void noFailure(J8Step0Test.Run r) {
        assertEquals("-", r.result().get("failure"), r.raw());
    }

    @Test
    void t4_zeroIterationItem_actorsBank_K1_K2() throws Exception {
        for (int k : new int[] {1, 2}) {
            J8Step0Test.Run r = split(H1 + "Actors:bank", "count", k);
            noFailure(r);
            assertEquals("true", r.result().get("split"));
            assertEquals("1", r.result().get("r"));
            assertEquals("2", r.result().get("n"));
            // item 1 (Backward reading Withdraw before Deposit: inconsistent) is empty; item 0 has 2 graphs
            assertEquals("-1:1:c 1:0: 0:2:cc", shape(r));
            assertEquals("3", r.counts().get("complete"));
            assertEquals("3", r.counts().get("ran"));
        }
    }

    @Test
    void t4_inconsistentEntryInsideItem_doesNotEndIt_rSs() throws Exception {
        J8Step0Test.Run r = split(H1 + "Litmus:r_ss", "count", 1);
        noFailure(r);
        assertEquals("1", r.result().get("n"));
        // iteration 2 pops an inconsistent Backward, then a consistent one: still item 0
        assertEquals("-1:1:c 0:2:cc", shape(r));
    }

    @Test
    void t4_lastGraphOfItemAttributedToIt_cleanupDelay() throws Exception {
        J8Step0Test.Run r = split(J8 + "CleanupDelay:0", "count", 1);
        noFailure(r);
        assertEquals("3", r.result().get("n"));
        // item 2's only graph is blocked and is counted in item 2, not in item 1
        assertEquals("-1:1:b 2:1:b 1:2:cc 0:2:cc", shape(r));
        assertEquals("4", r.counts().get("complete"));
        assertEquals("2", r.counts().get("blocked"));
        assertEquals("6", r.counts().get("ran"));
        assertEquals("2", r.counts().get("blockedIterations"));
    }

    @Test
    void t4_trailingInconsistentPop_misroute() throws Exception {
        J8Step0Test.Run r = split(J4 + "A2Store:misroute,3,1,2", "export", 1);
        noFailure(r);
        assertEquals("-1:1:c 2:1:c 1:2:cc 0:2:cc", shape(r));
    }

    @Test
    void t4_rootExhaustsBelowK_isTheSequentialRun() throws Exception {
        J8Step0Test.Run r = split(H1 + "Actors:bank", "count", 3);
        noFailure(r);
        assertEquals("false", r.result().get("split"));
        assertEquals("-1:3:ccc", shape(r));
        J8Step0Test.Run s = seq(H1 + "Actors:bank", "count");
        assertEquals(s.counts(), r.counts());
    }

    @Test
    void lateName_K1_refusedInName_K2_sequential() throws Exception {
        J8Step0Test.Run r = split(J8 + "LateName", "count", 1);
        assertTrue(r.result().get("failure").contains("m/2")
                && r.result().get("failure").contains("first created after the split boundary"), r.raw());
        J8Step0Test.Run r2 = split(J8 + "LateName", "count", 2);
        noFailure(r2);
        assertEquals("false", r2.result().get("split"));
        assertEquals("2", r2.counts().get("complete"));
        assertEquals("2", r2.counts().get("ran"));
    }

    @Test
    void reconstructionFailure_strictInSplitMode_K1_K2_sequentialAccepts() throws Exception {
        J8Step0Test.Run s = seq(J8 + "ReconstructionFailure", "count");
        noFailure(s);
        assertEquals("2", s.counts().get("complete"));
        for (int k : new int[] {1, 2}) {
            J8Step0Test.Run r = split(J8 + "ReconstructionFailure", "count", k);
            assertTrue(r.result().get("failure").contains("cannot normalise"), "K=" + k + "\n" + r.raw());
        }
    }

    @Test
    void daemonTidPayload_exportRefused_K1() throws Exception {
        J8Step0Test.Run r = split(J8 + "DaemonTidPayload", "export", 1);
        assertTrue(r.result().get("failure").contains("payload names daemon m/0"), r.raw());
        J8Step0Test.Run c = split(J8 + "DaemonTidPayload", "count", 1);
        noFailure(c);
        assertEquals("2", c.counts().get("complete"));
    }

    @Test
    void cleanupDelay_countsIndependentOfStaleCleanup() throws Exception {
        long t0 = System.nanoTime();
        J8Step0Test.Run fast = split(J8 + "CleanupDelay:0", "count", 1);
        long t1 = System.nanoTime();
        J8Step0Test.Run slow = split(J8 + "CleanupDelay:300", "count", 1);
        long t2 = System.nanoTime();
        noFailure(fast);
        noFailure(slow);
        assertEquals(fast.counts(), slow.counts());
        assertEquals(fast.segs(), slow.segs());
        assertEquals("-1:1:b 2:1:b 1:2:cc 0:2:cc", shape(slow));
        long fastMs = (t1 - t0) / 1_000_000;
        long slowMs = (t2 - t1) / 1_000_000;
        System.out.println("J8 cleanup delay: 0 ms run " + fastMs + " ms, 300 ms run " + slowMs + " ms");
        // two stale waits (after the blocked root iteration and after item 2's blocked iteration)
        assertTrue(slowMs - fastMs >= 400, "the delay took effect: " + fastMs + " vs " + slowMs);
    }
}
