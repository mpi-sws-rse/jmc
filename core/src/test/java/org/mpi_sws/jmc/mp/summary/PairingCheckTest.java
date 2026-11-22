package org.mpi_sws.jmc.mp.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mpi_sws.jmc.mp.summary.pairing.PairingFixture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The online check's pairing check ({@link PairingCheck}): each contract row's failure or warning, in TraceForge's
 * wording, against a program with visible and header roles {@code c0, c1}, model {@code p2p} and the tags
 * {@code Ping} (arity 1) and {@code Ack} (arity 0); and the collection of a program's tags from its package.
 */
class PairingCheckTest {

    @TempDir
    Path dir;

    static final SpecificationFile.Expected EXP =
            new SpecificationFile.Expected(List.of("c0", "c1"), "p2p", List.of("c0", "c1"));
    static final String HEADER = "{\"format\":1,\"minor\":1,\"tool\":\"jmc\",\"program\":\"p\",\"size\":\"s\","
            + "\"visible\":[\"c0\",\"c1\"],\"roles\":[\"c0\",\"c1\"],\"model\":\"p2p\",\"enumeration\":\"complete\","
            + "\"encoding\":1}";

    /** One send per role, {@code v} on c0 and 1 on c1; counts [0,0]. */
    static String line(String v) {
        return "{\"statuses\":{\"c0\":\"done\",\"c1\":\"done\"},\"words\":{\"c0\":[{\"s\":" + v + "}],"
                + "\"c1\":[{\"s\":1}]},\"counts\":[0,0],\"n\":1}";
    }

    static final String PING = line("{\"t\":\"Ping\",\"f\":[1]}");
    static final String ACK = line("{\"t\":\"Ack\",\"f\":[]}");

    static Map<String, SortedSet<Integer>> tags(Object... tagAndArities) {
        Map<String, SortedSet<Integer>> m = new TreeMap<>();
        for (int i = 0; i < tagAndArities.length; i += 2) {
            m.put((String) tagAndArities[i], new TreeSet<>(List.of((Integer[]) tagAndArities[i + 1])));
        }
        return m;
    }

    static final Map<String, SortedSet<Integer>> PROGRAM = tags("Ping", new Integer[] {1}, "Ack", new Integer[] {0});

    Path file(String... lines) throws Exception {
        Path p = Files.createTempFile(dir, "spec", ".jsonl");
        Files.write(p, List.of(lines), StandardCharsets.UTF_8);
        return p;
    }

    PairingCheck.Report check(Path p) throws Exception {
        return PairingCheck.check(p, EXP, PROGRAM);
    }

    static List<String> rendered(List<PairingCheck.Item> items) {
        return items.stream().map(i -> i.row() + ": " + i.what()).toList();
    }

    @Test
    void passes_withoutItems_whenEverythingMatches() throws Exception {
        PairingCheck.Report r = check(file(HEADER, PING, ACK));
        assertTrue(r.passed());
        assertEquals(List.of(), rendered(r.failures()));
        assertEquals(List.of(), rendered(r.warnings()));
    }

    @Test
    void c2_unsortedVisible_andListsThatDiffer() throws Exception {
        Path p = file(HEADER.replace("\"visible\":[\"c0\",\"c1\"]", "\"visible\":[\"c1\",\"c0\"]"), PING);
        assertEquals(List.of("C2: " + p + ": line 1: the `visible` list [\"c1\", \"c0\"] is not byte-sorted with"
                        + " distinct names",
                "C2: the two `visible` lists differ: [\"c1\", \"c0\"] and [\"c0\", \"c1\"]"),
                rendered(check(p).failures()));
        Path q = file(HEADER.replace("\"roles\":[\"c0\",\"c1\"]", "\"roles\":[\"c0\",\"c1\",\"x\"]"), PING);
        assertEquals(List.of("C2: the two `roles` lists differ: [\"c0\", \"c1\", \"x\"] and [\"c0\", \"c1\"]"),
                rendered(check(q).failures()));
    }

    @Test
    void c3_modelsDiffer_orAreUnsupported() throws Exception {
        assertEquals(List.of("C3: the two models differ: asyn and p2p"),
                rendered(check(file(HEADER.replace("\"p2p\"", "\"asyn\""), PING)).failures()));
        PairingCheck.Report cd = PairingCheck.check(file(HEADER.replace("\"p2p\"", "\"cd\""), PING),
                new SpecificationFile.Expected(List.of("c0", "c1"), "cd", List.of("c0", "c1")), PROGRAM);
        assertEquals(List.of("C3: the specification's model `cd` is not supported for a join (p2p or asyn)",
                "C3: the implementation's model `cd` is not supported for a join (p2p or asyn)"),
                rendered(cd.failures()));
    }

    @Test
    void c4_specificationNotComplete() throws Exception {
        Path p = file(HEADER.replace("\"complete\"", "\"sampled\",\"seed\":1,\"iterations\":3"), PING);
        assertEquals(List.of("C4: the specification is `sampled`, not `complete`"), rendered(check(p).failures()));
    }

    @Test
    void c6_repeatedLine_byNormalForm_andByText() throws Exception {
        Path p = file(HEADER, PING, ACK, PING.replace("\"n\":1", "\"n\":5"));
        assertEquals(List.of("C6: " + p + ": line 4 repeats an earlier line"), rendered(check(p).failures()));
        Path q = file(HEADER, "{\"x\":1}", "{\"x\":1}");
        assertEquals(List.of("C6: " + q + ": line 3 repeats an earlier line"), rendered(check(q).failures()));
    }

    @Test
    void c10_countAboveTheCommonBound() throws Exception {
        Path p = file(HEADER, PING.replace("\"counts\":[0,0]", "\"counts\":[2147483648,0]"));
        assertEquals(List.of("C10: " + p + ": line 2: the count 2147483648 is out of bounds (both readers accept"
                + " counts up to 2^31-1)"), rendered(check(p).failures()));
        Path q = file(HEADER, PING.replace("\"counts\":[0,0]", "\"counts\":[2147483647,0]"));
        assertTrue(check(q).passed(), rendered(check(q).failures()).toString());
    }

    @Test
    void c13_arityMismatchFails_oneSidedTagsWarn() throws Exception {
        Path p = file(HEADER, line("{\"t\":\"Ping\",\"f\":[1,2]}"), line("{\"t\":\"Nack\",\"f\":[]}"));
        PairingCheck.Report r = check(p);
        assertEquals(List.of("C13: the tag \"Ping\" has the field arities {2} in the specification and {1} in the"
                + " implementation"), rendered(r.failures()));
        assertEquals(List.of("C13: the tag \"Nack\" (arities {0}) occurs only in the specification; the"
                        + " implementation never sends it, which the join does not count against it",
                "C13: the tag \"Ack\" (arities {0}) occurs only in the implementation; the join reports the lines"
                        + " that carry it as uncovered"),
                rendered(r.warnings()));
    }

    /** A program may declare a tag with several arities; a specification using some of them passes. */
    @Test
    void c13_specificationUsingSomeOfTheProgramsArities_passes() throws Exception {
        PairingCheck.Report r = PairingCheck.check(file(HEADER, PING, ACK), EXP,
                tags("Ping", new Integer[] {1, 2}, "Ack", new Integer[] {0}));
        assertTrue(r.passed(), rendered(r.failures()).toString());
    }

    @Test
    void c18_identityTagWarns() throws Exception {
        Path p = file(HEADER, PING, ACK, line("{\"t\":\"Ping\",\"f\":[{\"t\":\"Tid\",\"f\":[]}]}"));
        PairingCheck.Report r = check(p);
        assertTrue(r.passed());
        assertTrue(rendered(r.warnings()).contains("C18: " + p + ": line 4: a value is tagged \"Tid\", the name of an"
                + " identity type; if it is a process identity, the programs must send its role name instead"),
                rendered(r.warnings()).toString());
    }

    /** A header or line the reader refuses is the reader's refusal, not a pairing failure. */
    @Test
    void refusedHeaderOrLine_leftToTheReader() throws Exception {
        Path p = file(HEADER.replace("\"encoding\":1}", "\"encoding\":1,\"x\":1}").replace("\"p2p\"", "\"asyn\""),
                PING);
        assertTrue(check(p).passed(), rendered(check(p).failures()).toString());
        Path q = file(HEADER, line("{\"t\":\"Ping\",\"f\":[1,2]}").replace("{\"s\":1}", "{\"s\": 1}"));
        assertTrue(check(q).passed(), rendered(check(q).failures()).toString());
    }

    @Test
    void programTags_fromThePackagesMessagesAndWhatTheyReach() {
        Map<String, SortedSet<Integer>> expected = tags("Ping", new Integer[] {1, 2}, "Put", new Integer[] {2},
                "Get", new Integer[] {1}, "Val", new Integer[] {1}, "RED", new Integer[] {0},
                "GREEN", new Integer[] {0}, "STOP", new Integer[] {0}, "None", new Integer[] {0},
                "Some", new Integer[] {1});
        assertEquals(expected, new TreeMap<>(PairingCheck.programTags(PairingFixture.class)));
    }
}
