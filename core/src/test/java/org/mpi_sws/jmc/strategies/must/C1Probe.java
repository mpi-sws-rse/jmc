package org.mpi_sws.jmc.strategies.must;

import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.programs.MustSection13;
import org.mpi_sws.jmc.mp.programs.j1.BlockedAlone;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;
import org.mpi_sws.jmc.mp.programs.j1.JoinChainSpawn;
import org.mpi_sws.jmc.mp.programs.j1.JoinChainSpawnEarly;
import org.mpi_sws.jmc.mp.programs.j1.ProcessAwaitsProcess;
import org.mpi_sws.jmc.mp.programs.j1.ProcessSpawnsProcess;
import org.mpi_sws.jmc.mp.programs.j1.SRnb;
import org.mpi_sws.jmc.mp.programs.j1.SpawnAfterJoin;
import org.mpi_sws.jmc.mp.programs.j1.SpawnBeforeJoin;
import org.mpi_sws.jmc.mp.programs.j2.NNR;
import org.mpi_sws.jmc.mp.programs.j2.NSR;
import org.mpi_sws.jmc.mp.programs.j2.NSRNVariant;
import org.mpi_sws.jmc.mp.programs.j2.NondetK;
import org.mpi_sws.jmc.mp.programs.j2.R3Model;
import org.mpi_sws.jmc.mp.programs.j2.SSR;
import org.mpi_sws.jmc.mp.programs.j2.SSRBR;
import org.mpi_sws.jmc.mp.programs.j2b.SpawnAfterAwait;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * JMC-SPEED tester round 01: the C1 differential probe. Runs in its own JVM, so that
 * {@code -Djmc.must.checkPrefixes} is fixed before {@link Graph} is loaded; {@link C1DifferentialTest}
 * launches it. Arguments: {@code guard=on|off} (checked against {@link Graph#CHECK}), then scenario
 * names. Prints {@code PASS <scenario>} or {@code FAIL <scenario> ...} per scenario and exits 0 only if
 * every scenario passed.
 *
 * <p>Every expected value below was derived by hand from the Must definition of porf
 * ({@code (po ∪ rf ∪ hb)⁺}, strict) before the probe was run; the derivations are in the comments.
 */
public final class C1Probe {

    private static final CommunicationModel P2P = CommunicationModel.P2P;
    private static final Predicate<MpMessage> ANY = m -> true;

    // Sparse task ids: slot order (first use) B, A, C, D, E differs from id order.
    static final long A = 3L;
    static final long B = 1_000_000_007L;
    static final long C = 42L;
    static final long D = Long.MAX_VALUE;
    static final long E = 0L;
    static final long Z = 77L;

    private C1Probe() {}

    static EventKey k(long t, long i) {
        return new EventKey(t, i);
    }

    static Set<EventKey> set(EventKey... ks) {
        return new TreeSet<>(Arrays.asList(ks));
    }

    static MpLabel send() {
        return new MpLabel.Send(P2P, Tid.of(1), new J1Fixture.V(0));
    }

    static MpLabel recv() {
        return new MpLabel.Recv(P2P, true);
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    static void eq(Object expected, Object actual, String what) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    /** The message of the IllegalStateException the action throws, or null if it returns. */
    static String ise(Runnable r) {
        try {
            r.run();
            return null;
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
    }

    static void out(String s) {
        System.out.println(s);
    }

    // ------------------------------------------------------------------ the hand-derived graph

    static final EventKey B0 = k(B, 0);
    static final EventKey B1 = k(B, 1);
    static final EventKey B2 = k(B, 2);
    static final EventKey A0 = k(A, 0);
    static final EventKey A1 = k(A, 1);
    static final EventKey A2 = k(A, 2);
    static final EventKey C0 = k(C, 0);
    static final EventKey C1 = k(C, 1);
    static final EventKey D0 = k(D, 0);
    static final EventKey E0 = k(E, 0);

    /**
     * Builds, in stamp order: b0 send; a0 recv ← b0; a1 send; b1 send; c0 recv (hbIn a1) ← b1;
     * a2 recv ⊥; b2 send; a2 ← b2 (stamp(b2) = 6 > stamp(a2) = 5, a2 has no successor); d0 send
     * (hbIn c0, a2); e0 nondet; c1 recv ⊥.
     */
    static Graph basic() {
        Graph g = new Graph();
        g.append(B, send(), null);                      // stamp 0: b0
        g.append(A, recv(), ANY);                       // stamp 1: a0
        g.setRf(A0, B0);
        g.append(A, send(), null);                      // stamp 2: a1
        g.append(B, send(), null);                      // stamp 3: b1
        g.append(C, recv(), ANY, Set.of(A1));           // stamp 4: c0
        g.setRf(C0, B1);
        g.append(A, recv(), ANY);                       // stamp 5: a2
        g.setBottom(A2);
        g.append(B, send(), null);                      // stamp 6: b2
        g.setRf(A2, B2);                                // backward-stamped rf
        g.append(D, send(), null, Set.of(C0, A2));      // stamp 7: d0
        g.append(E, new MpLabel.Nondet(2, 0), null);    // stamp 8: e0
        g.append(C, recv(), ANY);                       // stamp 9: c1
        g.setBottom(C1);
        return g;
    }

    /**
     * The strict porf prefixes of {@link #basic()}, derived by hand:
     * b0 {}; a0 {b0} (rf); a1 {a0, b0} (po); b1 {b0} (po); c0 = po-less, hb a1, rf b1:
     * {a1, a0, b0} ∪ {b1} ; a2 = po a1 ∪ rf b2: {a0, a1, b0} ∪ {b2, b0, b1}; b2 {b0, b1};
     * d0 = hb c0 ∪ hb a2: {c0, a0, a1, b0, b1} ∪ {a2, b2}; e0 {}; c1 = po c0: {c0, a0, a1, b0, b1}.
     */
    static Map<EventKey, Set<EventKey>> basicExpected() {
        Map<EventKey, Set<EventKey>> m = new LinkedHashMap<>();
        m.put(B0, set());
        m.put(A0, set(B0));
        m.put(A1, set(A0, B0));
        m.put(B1, set(B0));
        m.put(C0, set(A0, A1, B0, B1));
        m.put(A2, set(A0, A1, B0, B1, B2));
        m.put(B2, set(B0, B1));
        m.put(D0, set(A0, A1, A2, B0, B1, B2, C0));
        m.put(E0, set());
        m.put(C1, set(A0, A1, B0, B1, C0));
        return m;
    }

    /** Checks enumeration, membership on every pair, and the ExecutionGraph relation against {@code exp}. */
    static void expectAll(Graph g, Map<EventKey, Set<EventKey>> exp, String where) {
        eq(exp.size(), g.size(), where + " size");
        ExecutionGraph eg = g.toExecutionGraph();
        for (Map.Entry<EventKey, Set<EventKey>> e : exp.entrySet()) {
            Graph.Node n = g.node(e.getKey());
            eq(e.getValue(), new TreeSet<>(n.prefix()), where + " prefix() of " + e.getKey());
            for (EventKey x : exp.keySet()) {
                boolean want = e.getValue().contains(x);
                eq(want, n.inPrefix(x), where + " inPrefix " + x + " in " + e.getKey());
                eq(want, g.porfBefore(x, e.getKey()), where + " porfBefore " + x + " " + e.getKey());
                eq(want, eg.porfBefore(x, e.getKey()), where + " ExecutionGraph.porfBefore " + x + " " + e.getKey());
            }
        }
        g.checkPrefixes(where + " (explicit)");
    }

    static void scenarioBasic() {
        Graph g = basic();
        expectAll(g, basicExpected(), "basic");
        eq(10, g.nextStamp(), "nextStamp");
        eq(5, g.node(A2).stamp, "stamp a2");
        eq(6, g.node(B2).stamp, "stamp b2");
        eq(B2, g.node(A2).rf, "a2 reads b2");
    }

    /** Item 3: sparse ids are covered by basic(); here the unknown and out-of-range queries. */
    static void scenarioUnknownTid() {
        Graph g = basic();
        for (Graph.Node n : g.nodesByStamp()) {
            check(!n.inPrefix(k(999, 0)), "unknown tid 999 in " + n.key);
            check(!n.inPrefix(k(999, 5)), "unknown tid 999/5 in " + n.key);
            check(!n.inPrefix(k(Long.MIN_VALUE, 0)), "unknown tid MIN in " + n.key);
            check(!n.inPrefix(k(A, 3)), "(A,3) beyond the task in " + n.key);
            check(!n.inPrefix(k(B, 100)), "(B,100) beyond the task in " + n.key);
        }
        // a slot the node's clock does not reach: b0 was appended when only B had a slot
        check(!g.node(B0).inPrefix(C0), "c0 in b0");
        check(!g.node(B0).inPrefix(E0), "e0 in b0");
        // a slot assigned later in the lineage, by a copy only
        Graph g2 = g.copy();
        g2.append(Z, send(), null);
        for (Graph.Node n : g.nodesByStamp()) {
            check(!n.inPrefix(k(Z, 0)), "(Z,0) of a copy in " + n.key + " of the original");
        }
        check(!g.contains(k(Z, 0)), "original contains (Z,0)");
        ExecutionGraph eg = g.toExecutionGraph();
        check(!eg.porfBefore(k(999, 0), A2), "ExecutionGraph unknown tid");
        // negative indices (round 01 F4, fixed in round 02): absent everywhere, as in ExecutionGraph
        for (Graph.Node n : g.nodesByStamp()) {
            for (long t : new long[] {A, B, C, D, E}) {
                check(!n.inPrefix(k(t, -1)), "(" + t + ",-1) in " + n.key);
                check(!n.inPrefix(k(t, Long.MIN_VALUE)), "(" + t + ",MIN) in " + n.key);
            }
        }
        check(!eg.porfBefore(k(A, -1), A2) && !g.node(A2).prefix().contains(k(A, -1)), "ExecutionGraph (A,-1)");
    }

    /** A snapshot of everything observable about a graph's nodes. */
    static List<String> snapshot(Graph g, List<EventKey> probes) {
        List<String> s = new ArrayList<>();
        s.add("size " + g.size() + " next " + g.nextStamp() + " " + g);
        for (Graph.Node n : g.nodesByStamp()) {
            StringBuilder b = new StringBuilder(n.key + " stamp " + n.stamp + " rf " + n.rf + " bot " + n.bottom
                    + " clock " + Arrays.toString(n.clock) + " prefix " + new TreeSet<>(n.prefix()) + " in");
            for (EventKey p : probes) {
                b.append(n.inPrefix(p) ? '1' : '0');
            }
            s.add(b.toString());
            if (n.isSend()) {
                s.add("reader " + n.key + " " + g.readerOf(n.key));
            }
        }
        return s;
    }

    /** Item 4: mutating a copy (or a restriction) leaves the original untouched, and vice versa. */
    static void scenarioCopyIsolation() {
        Graph g = basic();
        List<EventKey> probes = new ArrayList<>(basicExpected().keySet());
        probes.add(k(Z, 0));
        probes.add(k(A, 3));
        List<String> before = snapshot(g, probes);

        // 1. the copy: setRf on a copied node first (same slot count as the original's clock)
        Graph g2 = g.copy();
        g2.setRf(C1, B2);                     // c1: ⊥ → b2; c1 has no successor
        eq(set(A0, A1, B0, B1, C0, B2), new TreeSet<>(g2.node(C1).prefix()), "copy c1 after setRf");
        g2.setBottom(C1);
        g2.setRf(C1, B2);
        g2.append(Z, send(), null);           // new task, new slot in the shared Slots
        g2.append(A, recv(), ANY);            // a3
        g2.setRf(k(A, 3), k(Z, 0));
        eq(set(A0, A1, A2, B0, B1, B2, k(Z, 0)), new TreeSet<>(g2.node(k(A, 3)).prefix()), "copy a3");
        g2.setBottom(k(A, 3));
        eq(set(A0, A1, A2, B0, B1, B2), new TreeSet<>(g2.node(k(A, 3)).prefix()), "copy a3 ⊥");
        g2.checkPrefixes("copy (explicit)");
        eq(before, snapshot(g, probes), "original after mutating the copy");
        expectAll(g, basicExpected(), "original after mutating the copy");
        eq(A2, g.readerOf(B2), "original readerOf b2");

        // 2. the other way: mutate the original, the earlier copy and a fresh copy stay put
        Graph h = g.copy();
        List<String> hBefore = snapshot(h, probes);
        List<String> g2Before = snapshot(g2, probes);
        g.setRf(C1, B1);
        eq(set(A0, A1, B0, B1, C0), new TreeSet<>(g.node(C1).prefix()), "original c1 ← b1");
        g.setRf(C1, B2);
        g.append(C, recv(), ANY);
        g.setRf(k(C, 2), B0);
        eq(hBefore, snapshot(h, probes), "copy after mutating the original");
        eq(g2Before, snapshot(g2, probes), "first copy after mutating the original");

        // 3. a restriction (keep everything of h) is isolated too
        Set<EventKey> all = new TreeSet<>();
        for (Graph.Node n : h.nodesByStamp()) {
            all.add(n.key);
        }
        Graph r = h.restrictTo(all);
        r.setRf(C1, B2);
        r.append(E, send(), null);
        eq(hBefore, snapshot(h, probes), "copy after mutating its restriction");
        expectAll(h, basicExpected(), "copy after mutating its restriction");
    }

    // ------------------------------------------------------------------ contracts (item 5, 6)

    /**
     * Item 5a: a restriction that removes a porf ancestor (via hb) of a kept event. x = (1,0) send;
     * y = (3,0) send, hbIn {x}; y1 = (3,1) recv ⊥. keep {y, y1}. True prefixes after the
     * restriction: y {}, y1 {y}; the kept clocks say y {x}, y1 {x, y}.
     */
    static void scenarioRestrictRemovesAncestor() {
        EventKey x = k(1, 0);
        EventKey y = k(3, 0);
        EventKey y1 = k(3, 1);
        Graph g = new Graph();
        g.append(1, send(), null);
        g.append(3, send(), null, Set.of(x));
        g.append(3, recv(), ANY);
        g.setBottom(y1);
        Graph[] out = new Graph[1];
        String msg = ise(() -> out[0] = g.restrictTo(Set.of(y, y1)));
        out("STATE restrictAncestor threw: " + msg);
        if (Graph.CHECK && msg == null) {
            // only under a mutant that drops restrictTo's check: does the next checked operation see it?
            out("STATE restrictAncestor unchecked; next copy() threw: " + ise(() -> out[0].copy()));
        }
        if (Graph.CHECK) {
            check(msg != null && msg.startsWith("prefix check after restrictTo: node " + y + " has "),
                    "guard reports the restriction at y: " + msg);
        } else {
            eq(null, msg, "guard off: restrictTo returns");
            Graph r = out[0];
            out("STATE guard off: y prefix " + new TreeSet<>(r.node(y).prefix()) + ", y1 prefix "
                    + new TreeSet<>(r.node(y1).prefix()) + ", contains x " + r.contains(x) + ", porfBefore(x,y) "
                    + r.porfBefore(x, y) + ", ExecutionGraph " + r.toExecutionGraph().porfBefore(x, y));
            eq(set(x), new TreeSet<>(r.node(y).prefix()), "stale y");
            eq(set(x, y), new TreeSet<>(r.node(y1).prefix()), "stale y1");
            check(r.porfBefore(x, y) && !r.toExecutionGraph().porfBefore(x, y), "clocks and ExecutionGraph disagree");
            String explicit = ise(() -> r.checkPrefixes("explicit"));
            check(explicit != null && explicit.contains("node " + y), "explicit check reports y: " + explicit);
        }
        // the transitive form: x; w = (2,0) hbIn {x}; v = (4,0) hbIn {w}; keep {w, v}
        Graph t = new Graph();
        t.append(1, send(), null);
        t.append(2, send(), null, Set.of(x));
        t.append(4, send(), null, Set.of(k(2, 0)));
        String msg2 = ise(() -> t.restrictTo(Set.of(k(2, 0), k(4, 0))));
        out("STATE restrictAncestor transitive threw: " + msg2);
        if (Graph.CHECK) {
            check(msg2 != null && msg2.startsWith("prefix check after restrictTo: node " + k(2, 0)), "transitive: " + msg2);
        } else {
            eq(null, msg2, "guard off: transitive restrictTo returns");
        }
    }

    /**
     * Item 5b: setRf on a receive with a po successor. x = (1,0) send; r = (2,0) recv ⊥; r1 = (2,1)
     * send; setRf(r, x). True prefix of r1 afterwards {r, x}; the stale clock says {r}.
     */
    static void scenarioSetRfPoSuccessor() {
        EventKey x = k(1, 0);
        EventKey r = k(2, 0);
        EventKey r1 = k(2, 1);
        Graph g = new Graph();
        g.append(1, send(), null);
        g.append(2, recv(), ANY);
        g.setBottom(r);
        g.append(2, send(), null);
        String msg = ise(() -> g.setRf(r, x));
        out("STATE setRfPoSuccessor threw: " + msg);
        if (Graph.CHECK) {
            eq("prefix check: rf target " + r + " has the porf successor " + r1, msg, "guard message");
            check(g.node(r).bottom && g.node(r).rf == null, "the guard fires before the change");
        } else {
            eq(null, msg, "guard off: setRf returns");
            out("STATE guard off: r prefix " + new TreeSet<>(g.node(r).prefix()) + ", r1 prefix "
                    + new TreeSet<>(g.node(r1).prefix()) + ", ExecutionGraph porf(x, r1) "
                    + g.toExecutionGraph().porfBefore(x, r1));
            eq(set(x), new TreeSet<>(g.node(r).prefix()), "r recomputed");
            eq(set(r), new TreeSet<>(g.node(r1).prefix()), "r1 stale");
            check(g.toExecutionGraph().porfBefore(x, r1), "ExecutionGraph has x porf r1");
            String explicit = ise(() -> g.checkPrefixes("explicit"));
            check(explicit != null && explicit.contains("node " + r1), "explicit check reports r1: " + explicit);
        }
    }

    /** Item 5b, the successor through hb only: z = (3,0) hbIn {r}; r is the last event of its task. */
    static void scenarioSetRfHbSuccessor() {
        EventKey x = k(1, 0);
        EventKey r = k(2, 0);
        EventKey z = k(3, 0);
        Graph g = new Graph();
        g.append(1, send(), null);
        g.append(2, recv(), ANY);
        g.setBottom(r);
        g.append(3, send(), null, Set.of(r));
        String msg = ise(() -> g.setRf(r, x));
        out("STATE setRfHbSuccessor threw: " + msg);
        if (Graph.CHECK) {
            eq("prefix check: rf target " + r + " has the porf successor " + z, msg, "guard message");
        } else {
            eq(null, msg, "guard off: setRf returns");
            eq(set(r), new TreeSet<>(g.node(z).prefix()), "z stale (true prefix {r, x})");
        }
    }

    /** Item 5b for setBottom: r reads x, r1 follows; setBottom(r). True r1 prefix {r}; stale {r, x}. */
    static void scenarioSetBottomSuccessor() {
        EventKey x = k(1, 0);
        EventKey r = k(2, 0);
        EventKey r1 = k(2, 1);
        Graph g = new Graph();
        g.append(1, send(), null);
        g.append(2, recv(), ANY);
        g.setRf(r, x);
        g.append(2, send(), null);
        String msg = ise(() -> g.setBottom(r));
        out("STATE setBottomSuccessor threw: " + msg);
        if (Graph.CHECK) {
            eq("prefix check: rf target " + r + " has the porf successor " + r1, msg, "guard message");
        } else {
            eq(null, msg, "guard off: setBottom returns");
            eq(set(r, x), new TreeSet<>(g.node(r1).prefix()), "r1 stale (true prefix {r})");
        }
    }

    /**
     * Item 6: a restriction removes the send a kept receive reads. x = (1,0) send; r = (2,0) recv ← x;
     * keep {r}. The kept clock of r says {x}; from scratch (rf source absent) {}.
     */
    static void scenarioRestrictRemovesRfSource() {
        EventKey x = k(1, 0);
        EventKey r = k(2, 0);
        Graph g = new Graph();
        g.append(1, send(), null);
        g.append(2, recv(), ANY);
        g.setRf(r, x);
        Graph[] out = new Graph[1];
        String msg = ise(() -> out[0] = g.restrictTo(Set.of(r)));
        out("STATE restrictRfSource threw: " + msg);
        if (Graph.CHECK) {
            eq("prefix check after restrictTo: node " + r + " has [" + x + "], from scratch []", msg, "guard message");
        } else {
            eq(null, msg, "guard off: restrictTo returns");
            Graph h = out[0];
            ExecutionGraph eg = h.toExecutionGraph();
            out("STATE guard off: r rf " + h.node(r).rf + ", prefix " + new TreeSet<>(h.node(r).prefix())
                    + ", ExecutionGraph rf " + eg.rf(r) + " bottom " + eg.readsBottom(r));
            eq(x, h.node(r).rf, "rf kept");
            eq(set(x), new TreeSet<>(h.node(r).prefix()), "stale prefix");
            check(eg.rf(r).isEmpty() && !eg.readsBottom(r), "ExecutionGraph: r has neither rf nor ⊥");
            // re-pointing (the old comment's "must be re-pointed by the caller"): r has no successor
            h.append(4, send(), null);
            h.setRf(r, k(4, 0));
            eq(set(k(4, 0)), new TreeSet<>(h.node(r).prefix()), "re-pointed");
            h.checkPrefixes("re-pointed (explicit)");
        }
    }

    /**
     * setRf to a send that is not in the graph. Round 02 (F3): the contract requires a present send,
     * and the guard reports the call itself: {@code prefix check: rf target (2,0) reads the absent send
     * (9,0)}, before any change. Guard off (unchanged from round 01): r = (2,0) ← (9,0) absent gets
     * prefix {}; once (9,0) is appended, from scratch r's prefix is {(9,0)}, but r's clock is not
     * recomputed.
     */
    static void scenarioAbsentRfSource() {
        EventKey r = k(2, 0);
        EventKey s = k(9, 0);
        Graph g = new Graph();
        g.append(2, recv(), ANY);
        String atSetRf = ise(() -> g.setRf(r, s));
        out("STATE absentRfSource setRf threw: " + atSetRf);
        if (Graph.CHECK) {
            eq("prefix check: rf target " + r + " reads the absent send " + s, atSetRf, "guard message at setRf");
            check(g.node(r).rf == null && !g.node(r).bottom, "the guard fires before the change");
            eq(set(), new TreeSet<>(g.node(r).prefix()), "r unchanged");
            return;
        }
        eq(null, atSetRf, "guard off: setRf to an absent send returns");
        eq(set(), new TreeSet<>(g.node(r).prefix()), "absent source not in the prefix");
        eq(s, g.node(r).rf, "rf recorded");
        check(g.toExecutionGraph().rf(r).isEmpty(), "no rf edge in the ExecutionGraph");
        g.checkPrefixes("absent (explicit)");
        String msg = ise(() -> g.append(9, send(), null));
        out("STATE absentRfThenAppend threw: " + msg);
        eq(null, msg, "guard off: append returns");
        eq(set(), new TreeSet<>(g.node(r).prefix()), "r stale (true prefix {(9,0)})");
        String explicit = ise(() -> g.checkPrefixes("explicit"));
        check(explicit != null && explicit.contains("node " + r), "explicit check reports r: " + explicit);
    }

    // ------------------------------------------------------------------ programs (items 1, 2, CD/MBOX)

    /** Must with an observer of every finished iteration's graph and of the stack (reflection). */
    static final class Observing extends MustStrategy {
        final Set<Object> backward = Collections.newSetFromMap(new IdentityHashMap<>());
        int graphs;
        int lateRfGraphs;
        int lateRfHbGraphs;
        int hbGraphs;
        int pairChecks;
        int unsupportedModelNodes;
        final List<String> problems = new ArrayList<>();
        private final Field graphField;
        private final Field stackField;

        Observing(long seed, String path) throws Exception {
            super(seed, path);
            graphField = MustStrategy.class.getDeclaredField("graph");
            graphField.setAccessible(true);
            stackField = MustStrategy.class.getDeclaredField("stack");
            stackField.setAccessible(true);
        }

        @Override
        public void resetIteration(int iteration) {
            try {
                observe();
            } catch (Throwable t) {
                problems.add("observer: " + t);
            }
            super.resetIteration(iteration);
        }

        private void observe() throws Exception {
            for (Object item : (Deque<?>) stackField.get(this)) {
                if (item.getClass().getSimpleName().equals("Backward")) {
                    backward.add(item);
                }
            }
            Graph g = (Graph) graphField.get(this);
            if (g == null || g.size() == 0) {
                return;
            }
            graphs++;
            boolean late = false;
            boolean lateHb = false;
            boolean hb = false;
            for (Graph.Node n : g.nodesByStamp()) {
                CommunicationModel model = n.label instanceof MpLabel.Send sl ? sl.model()
                        : n.label instanceof MpLabel.Recv rl ? rl.model() : null;
                if (model != null && model != CommunicationModel.P2P && model != CommunicationModel.ASYN) {
                    unsupportedModelNodes++;
                }
                if (n.rf != null && g.contains(n.rf) && g.node(n.rf).stamp > n.stamp) {
                    late = true;
                    for (EventKey p : g.node(n.rf).prefix()) {
                        if (!g.node(p).hbIn.isEmpty() || !g.node(n.rf).hbIn.isEmpty()) {
                            lateHb = true;
                        }
                    }
                }
                for (EventKey h : n.hbIn) {
                    if (g.contains(h)) {
                        hb = true;
                    }
                }
            }
            lateRfGraphs += late ? 1 : 0;
            lateRfHbGraphs += lateHb ? 1 : 0;
            hbGraphs += hb ? 1 : 0;
            String m = ise(() -> g.checkPrefixes("observer"));
            if (m != null) {
                problems.add(m);
            }
            ExecutionGraph eg = g.toExecutionGraph();
            for (Graph.Node x : g.nodesByStamp()) {
                for (Graph.Node y : g.nodesByStamp()) {
                    pairChecks++;
                    if (y.inPrefix(x.key) != eg.porfBefore(x.key, y.key)) {
                        problems.add("observer pair " + x.key + " " + y.key);
                    }
                }
            }
        }
    }

    /** A program and its derived outcome; {@code needle} non-null means the run must fail with it. */
    record Expect(Supplier<MpProgram> program, int complete, int blocked, String needle, boolean needBackward,
            boolean needHb) {}

    static final Map<String, Expect> PROGRAMS = new LinkedHashMap<>();

    static {
        // spawn/join (item 1): derived in the fixtures' comments; the J1 ones do tryRecv with no send: 1 graph
        PROGRAMS.put("SJRevisit", new Expect(C1SpawnJoinRevisit::new, 2, 0, null, false, true));
        PROGRAMS.put("SJBackward", new Expect(C1SpawnJoinBackward::new, 2, 0, null, true, true));
        PROGRAMS.put("SJChain", new Expect(C1SpawnJoinChain::new, 6, 0, null, false, true));
        PROGRAMS.put("SpawnAfterAwait", new Expect(SpawnAfterAwait::new, 1, 0, null, false, true));
        PROGRAMS.put("SpawnBeforeJoin", new Expect(SpawnBeforeJoin::new, 1, 0, null, false, false));
        PROGRAMS.put("SpawnAfterJoin", new Expect(SpawnAfterJoin::new, 1, 0, null, false, true));
        PROGRAMS.put("JoinChainSpawn", new Expect(JoinChainSpawn::new, 1, 0, null, false, true));
        PROGRAMS.put("JoinChainSpawnEarly", new Expect(JoinChainSpawnEarly::new, 1, 0, null, false, true));
        PROGRAMS.put("ProcessSpawnsProcess", new Expect(ProcessSpawnsProcess::new, 1, 0, null, false, true));
        PROGRAMS.put("ProcessAwaitsProcess", new Expect(ProcessAwaitsProcess::new, 1, 0, null, false, false));
        // backward revisits (item 2): counts as derived for J2 (MustCountsTest); Ex. 4.2 needs a revisit
        PROGRAMS.put("SSR-ex42", new Expect(() -> new SSR(1, 1, true), 2, 0, null, true, false));
        PROGRAMS.put("NSR3", new Expect(() -> new NSR(3), 3, 0, null, false, false));
        PROGRAMS.put("SSRBR-ex43", new Expect(SSRBR::new, 4, 0, null, false, false));
        PROGRAMS.put("MustSection13", new Expect(MustSection13::new, 2, 0, null, false, false));
        PROGRAMS.put("NondetK3SSR", new Expect(() -> new NondetK(3, true), 6, 0, null, false, false));
        PROGRAMS.put("NSRN4-receiverFirst", new Expect(
                () -> new NSRNVariant(4, false, CommunicationModel.P2P), 24, 0, null, false, false));
        PROGRAMS.put("NNR3-asyn", new Expect(() -> new NNR(3), 1, 0, null, false, false));
        PROGRAMS.put("SRnb", new Expect(SRnb::new, 2, 0, null, false, false));
        PROGRAMS.put("BlockedAlone", new Expect(BlockedAlone::new, 0, 1, null, false, false));
        // CD and MBOX: refused before any graph is built
        PROGRAMS.put("R3-cd-send", new Expect(() -> new R3Model("cd-send"), 0, 0,
                "uses model CD, which must does not support (p2p and asyn only)", false, false));
        PROGRAMS.put("R3-mbox-recv", new Expect(() -> new R3Model("mbox-recv"), 0, 0,
                "uses model MBOX, which must does not support (p2p and asyn only)", false, false));
        PROGRAMS.put("R3-cd-replay", new Expect(() -> new R3Model("cd-replay"), 1, 0,
                "uses model CD, which must does not support (p2p and asyn only)", false, false));
        PROGRAMS.put("R3-p2p", new Expect(() -> new R3Model("p2p"), 2, 0, null, false, false));
        PROGRAMS.put("R3-asyn", new Expect(() -> new R3Model("asyn"), 2, 0, null, false, false));
    }

    static void runProgram(String name) throws Exception {
        Expect x = PROGRAMS.get(name);
        check(x != null, "unknown program " + name);
        MpProgram p = x.program().get();
        String path = "build/test-results/speed-c1/" + name;
        Observing s = new Observing(1L, path);
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .strategyType("must")
                .strategyConstructor(c -> s)
                .numIterations(10000)
                .seed(1L)
                .reportPath(path)
                .build();
        JmcModelCheckerReport report = null;
        Throwable thrown = null;
        try {
            report = new JmcModelChecker(config).check(new JmcFunctionalTestTarget(p.getClass().getName(), p::run));
        } catch (Throwable t) {
            thrown = t;
        }
        StringBuilder all = new StringBuilder(report == null ? "" : String.valueOf(report.getErrorMessage()));
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            all.append(" | ").append(t);
        }
        out("RUN " + name + " complete=" + s.distinctCompleteGraphs() + " blocked=" + s.distinctBlockedGraphs()
                + " iterations=" + s.iterations() + " exhausted=" + s.exhausted() + " observed=" + s.graphs
                + " backwardItems=" + s.backward.size() + " lateRfGraphs=" + s.lateRfGraphs
                + " lateRfViaHbGraphs=" + s.lateRfHbGraphs + " hbGraphs=" + s.hbGraphs + " pairChecks=" + s.pairChecks
                + " unsupportedModelNodes=" + s.unsupportedModelNodes
                + " problems=" + s.problems.size() + " thrown=" + (thrown == null ? "none" : thrown.getClass().getSimpleName())
                + " message=" + all);
        for (String pr : s.problems.subList(0, Math.min(5, s.problems.size()))) {
            out("PROBLEM " + name + " " + pr);
        }
        eq(List.of(), s.problems, name + " observer problems");
        check(!all.toString().contains("prefix check") && !all.toString().contains("porf cycle"),
                name + " guard fired: " + all);
        if (x.needle() != null) {
            check(all.toString().contains(x.needle()), name + " refused with '" + x.needle() + "': " + all);
        } else {
            eq(null, thrown, name + " thrown");
            eq(true, s.exhausted(), name + " exhausted");
            eq(s.iterations(), s.distinctCompleteGraphs() + s.distinctBlockedGraphs(), name + " optimality");
        }
        eq(0, s.unsupportedModelNodes, name + " no CD or MBOX event in any observed graph");
        eq(x.complete(), s.distinctCompleteGraphs(), name + " complete");
        eq(x.blocked(), s.distinctBlockedGraphs(), name + " blocked");
        if (x.needBackward()) {
            check(s.backward.size() > 0 && s.lateRfGraphs > 0, name + " needs a backward revisit");
        }
        if (x.needHb()) {
            check(s.hbGraphs > 0, name + " needs an hb edge");
        }
        if (x.needHb() && x.needBackward()) {
            check(s.lateRfHbGraphs > 0, name + " needs a backward-stamped rf whose send has an hb predecessor");
        }
    }

    // ------------------------------------------------------------------ main

    static final Map<String, Runnable> UNIT = new HashMap<>();

    static {
        UNIT.put("basic", C1Probe::scenarioBasic);
        UNIT.put("unknownTid", C1Probe::scenarioUnknownTid);
        UNIT.put("copyIsolation", C1Probe::scenarioCopyIsolation);
        UNIT.put("restrictRemovesAncestor", C1Probe::scenarioRestrictRemovesAncestor);
        UNIT.put("setRfPoSuccessor", C1Probe::scenarioSetRfPoSuccessor);
        UNIT.put("setRfHbSuccessor", C1Probe::scenarioSetRfHbSuccessor);
        UNIT.put("setBottomSuccessor", C1Probe::scenarioSetBottomSuccessor);
        UNIT.put("restrictRemovesRfSource", C1Probe::scenarioRestrictRemovesRfSource);
        UNIT.put("absentRfSource", C1Probe::scenarioAbsentRfSource);
    }

    /**
     * Entry point.
     *
     * @param args {@code guard=on|off}, then scenario names ({@code all-unit}, a unit name, or
     *     {@code run:<program>}, {@code run:all})
     */
    public static void main(String[] args) {
        boolean wantGuard = args[0].equals("guard=on");
        out("CHECK " + Graph.CHECK);
        int failed = 0;
        if (Graph.CHECK != wantGuard) {
            out("FAIL guard: Graph.CHECK is " + Graph.CHECK + ", wanted " + wantGuard);
            failed++;
        }
        List<String> names = new ArrayList<>();
        for (String a : Arrays.asList(args).subList(1, args.length)) {
            if (a.equals("all-unit")) {
                names.addAll(new TreeSet<>(UNIT.keySet()));
            } else if (a.equals("run:all")) {
                for (String p : PROGRAMS.keySet()) {
                    names.add("run:" + p);
                }
            } else {
                names.add(a);
            }
        }
        for (String n : names) {
            try {
                if (n.startsWith("run:")) {
                    runProgram(n.substring(4));
                } else {
                    Runnable r = UNIT.get(n);
                    check(r != null, "unknown scenario " + n);
                    r.run();
                }
                out("PASS " + n);
            } catch (Throwable t) {
                failed++;
                out("FAIL " + n + " " + t);
            }
        }
        out("DONE failed=" + failed);
        System.out.flush();
        Runtime.getRuntime().halt(failed == 0 ? 0 : 1);
    }
}
