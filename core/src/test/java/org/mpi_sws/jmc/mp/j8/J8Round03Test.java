package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.mp.graph.GraphEvent;
import org.mpi_sws.jmc.strategies.must.split.TypedGraphEncoding;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/**
 * J8 tester round 03 (plan/jmc-must/log/dev/J8/round-03.prompt.md): Codex's step-0 findings M1
 * (unpaired surrogates merged by UTF-8 replacement; J8-PLAN revision 8 M1), m1 (threads ordered by
 * creation-path name, §4(a)) and M2 (late class initialisation; revision 8 M2).
 */
class J8Round03Test {

    private static final char HI = (char) 0xD800;
    private static final char LO = (char) 0xDC00;
    /** Unpaired high, unpaired low, and a reversed (low then high) pair. */
    private static final List<String> BAD = List.of(String.valueOf(HI), String.valueOf(LO), "" + LO + HI,
            "a" + HI + "b");
    /** U+1F600 as a well-formed surrogate pair. */
    private static final String GRIN = new String(Character.toChars(0x1F600));

    record Str(String s) implements MpMessage {}

    record Outer(Object in) implements MpMessage {}

    static final LongFunction<String> NAMES = id -> switch ((int) id) {
        case 1 -> "m";
        case 2 -> "m/0";
        case 3 -> "m/1";
        default -> null;
    };

    static GraphCanon sendOf(MpMessage m) {
        return new ExecutionGraph.Builder()
                .add(GraphEvent.of(new EventKey(3, 0), new MpLabel.Send(CommunicationModel.P2P, Tid.of(2), m)))
                .build().canon();
    }

    /** The pre-fix encoding of a string value: Reading A with no well-formedness check. */
    static String oldS(String s) {
        return "1:S1:1" + s.getBytes(StandardCharsets.UTF_8).length + ":" + s;
    }

    @Test
    void m1_unpairedSurrogates_refused_alone_nested_inList_inOutcome() {
        for (String s : BAD) {
            String what = "string with chars " + s.chars().mapToObj(Integer::toHexString).toList();
            assertThrows(IllegalArgumentException.class, () -> TypedGraphEncoding.value(s, NAMES), what + " alone");
            assertThrows(IllegalArgumentException.class, () -> TypedGraphEncoding.value(new Str(s), NAMES),
                    what + " in a record");
            assertThrows(IllegalArgumentException.class,
                    () -> TypedGraphEncoding.value(new Outer(new Str(s)), NAMES), what + " in a nested record");
            assertThrows(IllegalArgumentException.class, () -> TypedGraphEncoding.value(List.of("x", s), NAMES),
                    what + " in a List");
            assertThrows(IllegalArgumentException.class,
                    () -> TypedGraphEncoding.value(new Outer(List.of(s)), NAMES), what + " in a List in a record");
            GraphCanon g = sendOf(new Outer(List.of(new Str(s))));
            assertThrows(IllegalArgumentException.class, () -> TypedGraphEncoding.outcome(g, NAMES),
                    what + " in a graph outcome (Send payload)");
        }
    }

    @Test
    void m1_wellFormedPair_accepted_distinctFromQuestionMark_oldBytesCollided() {
        // accepted, Reading A: "1:S1:14:" + 4 UTF-8 bytes
        assertEquals("1:S1:14:" + GRIN, TypedGraphEncoding.value(GRIN, NAMES));
        assertEquals("1:S1:11:?", TypedGraphEncoding.value("?", NAMES));
        assertEquals("1:S1:16:a" + GRIN + "b", TypedGraphEncoding.value("a" + GRIN + "b", NAMES));
        // digest level: the pair and "?" give different outcome digests
        String dGrin = TypedGraphEncoding.sha256(TypedGraphEncoding.outcome(sendOf(new Str(GRIN)), NAMES));
        String dQ = TypedGraphEncoding.sha256(TypedGraphEncoding.outcome(sendOf(new Str("?")), NAMES));
        assertNotEquals(dGrin, dQ);
        // Before the fix: an unpaired surrogate and "?" had the same UTF-8 bytes, framed lengths and digests.
        assertEquals(TypedGraphEncoding.value("?", NAMES), oldS("?"), "oldS is Reading A on well-formed input");
        for (String s : List.of(String.valueOf(HI), String.valueOf(LO))) {
            assertArrayEquals("?".getBytes(StandardCharsets.UTF_8), s.getBytes(StandardCharsets.UTF_8));
            assertNotEquals("?", s);
            assertArrayEquals(oldS("?").getBytes(StandardCharsets.UTF_8), oldS(s).getBytes(StandardCharsets.UTF_8));
            assertEquals(TypedGraphEncoding.sha256(oldS("?")), TypedGraphEncoding.sha256(oldS(s)));
            // whole outcome, as the old code would have produced it: only the string field differs
            String newQ = TypedGraphEncoding.outcome(sendOf(new Str("?")), NAMES);
            assertTrue(newQ.contains("1:S1:11:?"), newQ);
            String oldBad = newQ.replace("1:S1:11:?", oldS(s));
            assertNotEquals(newQ, oldBad);
            assertEquals(dQ, TypedGraphEncoding.sha256(oldBad), "the old outcome digests collided");
            // GraphCanon distinguished them all along
            assertNotEquals(sendOf(new Str("?")), sendOf(new Str(s)));
        }
    }

    /** A graph with only Nondet/Block labels, so each name first appears in its thread entry. */
    static String threadOrder(LongFunction<String> names, long[] ids, int[] labelCounts) {
        ExecutionGraph.Builder b = new ExecutionGraph.Builder();
        for (int t = 0; t < ids.length; t++) {
            for (int i = 0; i < labelCounts[t]; i++) {
                b.add(GraphEvent.of(new EventKey(ids[t], i), new MpLabel.Nondet(2, i % 2)));
            }
        }
        String enc = TypedGraphEncoding.outcome(b.build().canon(), names);
        List<int[]> at = new ArrayList<>();
        List<String> found = new ArrayList<>();
        for (long id : ids) {
            String n = names.apply(id);
            int pos = enc.indexOf(n.length() + ":" + n);
            assertTrue(pos >= 0, n + " in " + enc);
            at.add(new int[] {pos, found.size()});
            found.add(n);
        }
        at.sort((x, y) -> Integer.compare(x[0], y[0]));
        List<String> order = new ArrayList<>();
        at.forEach(x -> order.add(found.get(x[1])));
        return String.join(" ", order);
    }

    @Test
    void m1_threadsInCreationPathNameOrder() {
        // id order differs from name order, and count-first (the old composite order) puts m/1 first
        LongFunction<String> n1 = id -> id == 2 ? "m/1" : id == 3 ? "m/0" : null;
        assertEquals("m/0 m/1", threadOrder(n1, new long[] {2, 3}, new int[] {1, 2}));
        // m/1 (1 label), m/10 (3 labels), m/2 (2 labels): name order m/1 < m/10 < m/2;
        // count-first would give m/1, m/2, m/10
        LongFunction<String> n2 = id -> id == 2 ? "m/2" : id == 3 ? "m/10" : id == 4 ? "m/1" : null;
        assertEquals("m/1 m/10 m/2", threadOrder(n2, new long[] {2, 3, 4}, new int[] {2, 3, 1}));
    }

    // ---------------------------------------------------------------- run-level fixtures (fresh JVMs)

    private static final String J8 = "org.mpi_sws.jmc.mp.j8.";

    static String observed(J8Step0Test.Run r) {
        for (String l : r.raw().split("\n")) {
            if (l.startsWith("OBSERVED ")) {
                return l.substring(9);
            }
        }
        return "";
    }

    static J8Step0Test.Run run(String... args) throws Exception {
        J8Step0Test.Run r = J8Step0Test.probe(args);
        System.out.println("J8R3 " + String.join(" ", args) + "\n" + r.raw());
        return r;
    }

    @Test
    void m1_runLevel_surrogatePayload_refusedInSplitMode_acceptedSequentially() throws Exception {
        J8Step0Test.Run s = run("seq", J8 + "SurrogatePayload", "count");
        assertEquals("-", s.result().get("failure"), s.raw());
        assertEquals("2", s.counts().get("complete"));
        for (String k : List.of("1", "2")) {
            J8Step0Test.Run r = run("split", J8 + "SurrogatePayload", "count", k, "SEQUENTIAL", "-");
            assertTrue(r.result().get("failure").contains("not well-formed UTF-16"), "K=" + k + "\n" + r.raw());
        }
    }

    @Test
    void m2_lateClassInit_sequential_normal_reversed_isolated() throws Exception {
        String p = J8 + "LateClassInit";
        J8Step0Test.Run seq = run("seq", p, "count");
        J8Step0Test.Run full = run("split", p, "count", "1", "SEQUENTIAL", "-");
        J8Step0Test.Run rev = run("split", p, "count", "1", "REVERSED", "-");
        J8Step0Test.Run iso0 = run("split", p, "count", "1", "SEQUENTIAL", "0");
        J8Step0Test.Run iso1 = run("split", p, "count", "1", "SEQUENTIAL", "1");
        for (J8Step0Test.Run r : List.of(seq, full, rev, iso0, iso1)) {
            assertEquals("-", r.result().get("failure"), r.raw());
        }
        // sequential: 3 complete graphs; Late first initialised in iteration 2 (main reads c)
        assertEquals("3", seq.counts().get("complete"));
        assertEquals("3", seq.counts().get("ran"));
        assertEquals("lateinit runs=3 initialisedAtStart=000 clinitRuns=1 clinitAtRun=2", observed(seq));
        // normal order: R 1, N 2, items 1 then 0, one graph each; Late first initialised in item 0
        // (iteration 2 >= R), after the root
        assertEquals("1", full.result().get("r"));
        assertEquals("2", full.result().get("n"));
        List<J8Step0Test.Seg> fs = full.segs();
        assertEquals(List.of(-1, 1, 0), fs.stream().map(J8Step0Test.Seg::index).toList());
        assertEquals(List.of(1, 1, 1), fs.stream().map(J8Step0Test.Seg::iterations).toList());
        assertEquals("lateinit runs=3 initialisedAtStart=000 clinitRuns=1 clinitAtRun=2", observed(full));
        List<String> concat = new ArrayList<>();
        fs.forEach(x -> concat.addAll(x.outcomes()));
        assertEquals(seq.outs(), concat);
        assertEquals(seq.counts(), full.counts());
        // reversed: item 0 first (Late initialised in iteration 1), then item 1 runs with Late already
        // initialised; every item's result equal to the normal order's
        assertEquals(List.of(-1, 0, 1), rev.segs().stream().map(J8Step0Test.Seg::index).toList());
        assertEquals("lateinit runs=3 initialisedAtStart=001 clinitRuns=1 clinitAtRun=1", observed(rev));
        for (J8Step0Test.Seg x : rev.segs()) {
            assertEquals(fs.stream().filter(y -> y.index() == x.index()).findFirst().orElseThrow(), x);
        }
        assertEquals(full.counts(), rev.counts());
        assertEquals(full.result().get("fp"), rev.result().get("fp"));
        // isolated item 0 (fresh JVM): Late first initialised in its item; isolated item 1: never
        assertEquals("lateinit runs=2 initialisedAtStart=00 clinitRuns=1 clinitAtRun=1", observed(iso0));
        assertEquals("lateinit runs=2 initialisedAtStart=00 clinitRuns=0 clinitAtRun=-1", observed(iso1));
        assertEquals(fs.get(2), iso0.segs().get(1));
        assertEquals(fs.get(1), iso1.segs().get(1));
        assertEquals(full.result().get("fp"), iso0.result().get("fp"));
        assertEquals(full.result().get("fp"), iso1.result().get("fp"));
        // the late item's outcome (payload V(10 * SALT), SALT = 1) is the sequential run's third
        assertEquals(seq.outs().get(2), fs.get(2).outcomes().get(0));
    }
}
