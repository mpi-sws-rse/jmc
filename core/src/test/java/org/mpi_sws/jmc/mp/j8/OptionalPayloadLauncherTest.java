package org.mpi_sws.jmc.mp.j8;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * P2 round 01 (tester): Optional and nested records through the launcher ({@link J8Launch}, a fresh
 * {@code MpLauncher} JVM per run), fixture {@link OptionalPayload}. Both export modes (sequential, and
 * {@code --split 2 --k 1} as the J8 tests run it) must export the same lines; the online check against a
 * specification in TraceForge's shape (tool traceforge, no {@code n}, {@code Some}/{@code None} written as
 * TraceForge's export writes {@code Option}) must find nothing uncovered. Expected lines derived by hand from
 * plan/jmc-must/SUMMARY-FORMAT.md: srv is the only visible role, so {@code "counts":[]}; srv's word is the
 * order in which it received the five messages, each sender's order kept (P2P).
 */
class OptionalPayloadLauncherTest {

    static final String OP = J8Launch.J8 + "OptionalPayload:";

    // ---- values (TF_* quoted from TraceForge's ewd998 export; the others derived from the format)
    static final String IDLE_SOME = "{\"t\":\"Idle\",\"f\":[0,{\"t\":\"Some\",\"f\":[1]},2]}";
    static final String IDLE_NONE = "{\"t\":\"Idle\",\"f\":[0,{\"t\":\"None\",\"f\":[]},0]}";
    static final String RES_GOT = "{\"t\":\"Res\",\"f\":[{\"t\":\"Got\",\"f\":[5,{\"t\":\"Some\",\"f\":[7]}]}]}";
    static final String WRAP_SOME = "{\"t\":\"Wrap\",\"f\":[{\"t\":\"Some\",\"f\":[{\"t\":\"Inner\",\"f\":[3]}]}]}";
    static final String WRAP_NONE = "{\"t\":\"Wrap\",\"f\":[{\"t\":\"None\",\"f\":[]}]}";
    static final List<String> FROM_A = List.of(IDLE_SOME, RES_GOT);
    static final List<String> FROM_B = List.of(IDLE_NONE, WRAP_SOME, WRAP_NONE);

    static final String GRAPHS_VISIBLE = "10 distinct complete, 0 distinct blocked; stack exhausted";
    static final String HEADER_VISIBLE = "{\"format\":1,\"minor\":1,\"tool\":\"jmc\","
            + "\"program\":\"j8-optional\",\"size\":\"visible\","
            + "\"visible\":[\"srv\"],"
            + "\"roles\":[\"srv\"],\"model\":\"p2p\",\"enumeration\":\"complete\",\"encoding\":1}";
    static final String TF_HEADER_VISIBLE = "{\"format\":1,\"tool\":\"traceforge\",\"program\":\"j8-optional\","
            + "\"size\":\"visible\",\"visible\":[\"srv\"],\"model\":\"p2p\",\"enumeration\":\"complete\",\"encoding\":1}";
    static final String HEADER_INVISIBLE = "{\"format\":1,\"minor\":1,\"tool\":\"jmc\","
            + "\"program\":\"j8-optional\",\"size\":"
            + "\"invisible\",\"visible\":[\"srv\"],"
            + "\"roles\":[\"srv\"],\"model\":\"p2p\",\"enumeration\":\"complete\",\"encoding\":1}";
    static final String LINE_INVISIBLE = "{\"statuses\":{\"srv\":\"done\"},\"words\":{\"srv\":[{\"r\":{\"t\":\"V\","
            + "\"f\":[1]}}]},\"counts\":[],\"n\":2}";
    static final String NULL_AT = "(use Optional for an absent value) (field res.cas of message "
            + "org.mpi_sws.jmc.mp.j8.OptionalPayload$Msg$Res)";

    /** Every interleaving of {@code a} and {@code b} keeping the order of each. */
    static List<List<String>> interleavings(List<String> a, List<String> b) {
        List<List<String>> out = new ArrayList<>();
        if (a.isEmpty() || b.isEmpty()) {
            List<String> l = new ArrayList<>(a);
            l.addAll(b);
            out.add(l);
            return out;
        }
        for (List<String> rest : interleavings(a.subList(1, a.size()), b)) {
            List<String> l = new ArrayList<>(List.of(a.get(0)));
            l.addAll(rest);
            out.add(l);
        }
        for (List<String> rest : interleavings(a, b.subList(1, b.size()))) {
            List<String> l = new ArrayList<>(List.of(b.get(0)));
            l.addAll(rest);
            out.add(l);
        }
        return out;
    }

    /** srv's summary line for one receive order, without {@code n} (TraceForge's shape). */
    static String lineWithoutN(List<String> received) {
        List<String> words = new ArrayList<>();
        for (String v : received) {
            words.add("{\"r\":" + v + "}");
        }
        return "{\"statuses\":{\"srv\":\"done\"},\"words\":{\"srv\":[" + String.join(",", words) + "]},\"counts\":[]}";
    }

    static String withN(String lineWithoutN, int n) {
        return lineWithoutN.substring(0, lineWithoutN.length() - 1) + ",\"n\":" + n + "}";
    }

    /** The derived body of the visible fixture: 10 lines, one graph each. */
    static Set<String> derivedVisibleBody() {
        Set<String> body = new HashSet<>();
        for (List<String> order : interleavings(FROM_A, FROM_B)) {
            body.add(withN(lineWithoutN(order), 1));
        }
        assertEquals(10, body.size(), "C(5, 2) distinct orders");
        return body;
    }

    static List<String> lines(J8Launch.Out o) throws Exception {
        assertNotNull(o.exportFile());
        assertTrue(Files.exists(o.exportFile()), "export written: " + o.exportFile() + "\n" + o.text());
        return Files.readAllLines(o.exportFile(), StandardCharsets.UTF_8);
    }

    /** A specification file under J8Launch's directory (absolute path; the launcher's cwd is Gradle's). */
    static Path spec(String name, List<String> lines) throws Exception {
        Files.createDirectories(J8Launch.DIR);
        Path p = J8Launch.DIR.resolve("spec-" + System.currentTimeMillis() + "-" + name + ".jsonl").toAbsolutePath();
        Files.write(p, lines, StandardCharsets.UTF_8);
        return p;
    }

    // ---------------------------------------------------------------- export, both modes

    @Test
    void export_visibleOptionalsAndNestedRecords_sequentialAndSplitIdentical_expectedWords() throws Exception {
        J8Launch.Out seq = J8Launch.launch(OP + "visible", true);
        J8Step1Test.complete(seq, GRAPHS_VISIBLE);
        List<String> s = lines(seq);
        assertEquals(HEADER_VISIBLE, s.get(0), "header");
        assertEquals(derivedVisibleBody(), new HashSet<>(s.subList(1, s.size())), "body = the 10 derived lines");
        assertEquals(11, s.size(), "header + 10 lines, no duplicate");
        for (String v : List.of(IDLE_SOME, IDLE_NONE, RES_GOT, WRAP_SOME, WRAP_NONE)) {
            assertTrue(s.stream().anyMatch(l -> l.contains("{\"r\":" + v + "}")), "word {\"r\":" + v + "}");
        }

        J8Launch.Out split = J8Launch.launch(OP + "visible", true, "--split", "2", "--k", "1");
        System.out.println("P2 optional visible split: " + split.field("split"));
        J8Step1Test.complete(split, GRAPHS_VISIBLE);
        List<String> p = lines(split);
        assertEquals(new HashSet<>(s), new HashSet<>(p), "split and sequential exports equal as line sets");
        assertEquals(s.size(), p.size());
        assertArrayEquals(seq.export(), split.export(), "and byte-identical (sorted body)");
    }

    @Test
    void export_optionalOnlyInInvisibleMessage_splitRuns_equalSequential() throws Exception {
        // 283f3482: refused by split mode with "split mode refuses a payload value of java.util.Optional"
        String graphs = "2 distinct complete, 0 distinct blocked; stack exhausted";
        J8Launch.Out seq = J8Launch.launch(OP + "invisible", true);
        J8Step1Test.complete(seq, graphs);
        assertEquals(List.of(HEADER_INVISIBLE, LINE_INVISIBLE), lines(seq));

        J8Launch.Out split = J8Launch.launch(OP + "invisible", true, "--split", "2", "--k", "1");
        System.out.println("P2 optional invisible split: " + split.field("split") + " FAILED " + split.field("FAILED"));
        J8Step1Test.complete(split, graphs);
        assertEquals(new HashSet<>(lines(seq)), new HashSet<>(lines(split)), "line sets equal");
        assertArrayEquals(seq.export(), split.export());

        // counting mode (no export), as J8Step1Test.t7 runs its fixture
        J8Launch.Out counting = J8Launch.launch(OP + "invisible", false, "--split", "2", "--k", "1");
        J8Step1Test.complete(counting, graphs);
        assertTrue(counting.field("FAILED") == null, counting.text());
    }

    /** The FAILED and error lines of a run. */
    static List<String> failureLines(J8Launch.Out o) {
        List<String> out = new ArrayList<>();
        for (String l : o.text().split("\n")) {
            if (l.startsWith("FAILED:") || l.startsWith("error:")) {
                out.add(l);
            }
        }
        return out;
    }

    @Test
    void export_visibleNullComponent_refusedNamingFieldAndMessage_sequentialAndSplit() throws Exception {
        // control: counting mode encodes no summary, so the program itself runs (one graph)
        J8Step1Test.complete(J8Launch.launch(OP + "null", false), "1 distinct complete, 0 distinct blocked; stack exhausted");

        J8Launch.Out seq = J8Launch.launch(OP + "null", true);
        System.out.println("P2 optional null seq: " + seq.field("FAILED"));
        assertEquals(1, seq.exit(), seq.text());
        assertNotNull(seq.field("FAILED"), seq.text());
        List<String> f = failureLines(seq);
        assertTrue(f.stream().anyMatch(l -> l.contains("null is not a value: the format allows null only as ")
                && l.contains(NULL_AT)), "FAILED/error line names field and message: " + f + "\n" + seq.text());
        assertFalse(seq.text().contains("cannot read component"), seq.text());
        List<String> s = lines(seq);
        assertEquals(1, s.size(), "header only: " + s);
        assertTrue(s.get(0).contains("\"enumeration\":\"aborted\""), s.get(0));

        J8Launch.Out split = J8Launch.launch(OP + "null", true, "--split", "2", "--k", "1");
        System.out.println("P2 optional null split export: " + split.field("FAILED"));
        J8Step1Test.aborted(split, NULL_AT);
        assertFalse(split.text().contains("cannot read component"), split.text());

        J8Launch.Out counting = J8Launch.launch(OP + "null", false, "--split", "2", "--k", "1");
        System.out.println("P2 optional null split counting: " + counting.field("FAILED"));
        J8Step1Test.aborted(counting, "split mode refuses a null payload value: null is allowed only as ");
        assertTrue(counting.field("FAILED").contains(NULL_AT), counting.field("FAILED"));
    }

    // ---------------------------------------------------------------- online check (--spec)

    static List<String> tfSpec(String dropWithoutN) {
        List<String> out = new ArrayList<>(List.of(TF_HEADER_VISIBLE));
        for (List<String> order : interleavings(FROM_A, FROM_B)) {
            String l = lineWithoutN(order);
            if (!l.equals(dropWithoutN)) {
                out.add(l);
            }
        }
        return out;
    }

    @Test
    void online_traceForgeShapedSomeNoneSpec_zeroUncovered_sequentialAndSplit() throws Exception {
        List<String> text = tfSpec(null);
        assertEquals(11, text.size());
        assertTrue(text.stream().anyMatch(l -> l.contains("{\"t\":\"None\",\"f\":[]}")));
        assertTrue(text.stream().anyMatch(l -> l.contains("{\"t\":\"Some\",\"f\":[1]}")));
        Path spec = spec("optional-full", text);

        J8Launch.Out seq = J8Launch.launch(OP + "visible", false, "--spec", spec.toString());
        J8Step1Test.complete(seq, GRAPHS_VISIBLE);
        assertEquals("conforms", seq.field("verdict"), seq.text());
        assertEquals("0 graphs (0 distinct summaries)", seq.field("uncovered"), seq.text());

        J8Launch.Out split = J8Launch.launch(OP + "visible", false, "--spec", spec.toString(), "--split", "2", "--k", "1");
        J8Step1Test.complete(split, GRAPHS_VISIBLE);
        assertEquals("conforms", split.field("verdict"), split.text());
        assertEquals("0 graphs (0 distinct summaries)", split.field("uncovered"), split.text());
    }

    @Test
    void online_traceForgeShapedSpecMissingOneLine_reportsExactlyThatLine() throws Exception {
        // control for the zero-uncovered test: the order a, a, b, b, b is left out of the specification
        String dropped = lineWithoutN(List.of(IDLE_SOME, RES_GOT, IDLE_NONE, WRAP_SOME, WRAP_NONE));
        List<String> text = tfSpec(dropped);
        assertEquals(10, text.size());
        Path spec = spec("optional-miss", text);
        J8Launch.Out seq = J8Launch.launch(OP + "visible", false, "--spec", spec.toString());
        J8Step1Test.complete(seq, GRAPHS_VISIBLE);
        assertEquals("reports", seq.field("verdict"), seq.text());
        assertEquals("1 graphs (1 distinct summaries)", seq.field("uncovered"), seq.text());
        assertEquals(withN(dropped, 1), seq.field("witness"), seq.text());
    }
}
