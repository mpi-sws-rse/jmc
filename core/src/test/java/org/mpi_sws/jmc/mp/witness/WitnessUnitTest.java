package org.mpi_sws.jmc.mp.witness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.atIteration;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.field;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.launch;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.lines;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.witness.WitnessSupport.Out;
import org.mpi_sws.jmc.mp.witness.WitnessSupport.Witness;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * WITNESS item 4, test 1 (tester): the witness of a tiny MpProgram ({@link WitnessTiny}) against a
 * two-line specification export (header + one summary line).
 *
 * <p>Derived by hand before the run. WitnessTiny has four executions, one summary each: srv reads
 * A(1 + 10x) and A(2) in either order, x in {0, 1} chosen by main before its spawns. The
 * specification keeps the line of x = 0, c1's send read first ({@code [A(1), A(2)]}, body line 2 of
 * the sorted export), so three summaries are uncovered, each by one execution. The witness of the
 * execution x = 1, c2's send read first: main is thread 0 with BEGIN, NONDET 1 (k 1, before the
 * creations), TCREATE of srv, c1, c2 (tids 1, 2, 3 in creation order, k 2–4), the joins of srv, c1, c2
 * in the order main awaits them (TJOIN, k 5–7), END; srv's first receive reads c2's send (3, 1), its second
 * c1's (2, 1), each naming the sender's thread; c1 and c2 are invisible and printed. The
 * iteration is the run's (the exploration order is not derived); the three iterations are checked
 * to be distinct and increasing in n.
 */
class WitnessUnitTest {

    static final String TINY = WitnessSupport.PKG + "WitnessTiny";
    static final String L_1_2 = "{\"statuses\":{\"srv\":\"done\"},\"words\":{\"srv\":[{\"r\":{\"t\":\"A\",\"f\":[1]}},"
            + "{\"r\":{\"t\":\"A\",\"f\":[2]}}]},\"counts\":[],\"n\":1}";
    static final String L_2_11 = "{\"statuses\":{\"srv\":\"done\"},\"words\":{\"srv\":[{\"r\":{\"t\":\"A\",\"f\":[2]}},"
            + "{\"r\":{\"t\":\"A\",\"f\":[11]}}]},\"counts\":[],\"n\":1}";

    /** The derived witness of x = 1, c2 first, at iteration {@code i}. */
    static String derived(int i) {
        return lines(
                "uncovered summary (iteration " + i + "): " + L_2_11,
                "thread \"main\"[tid=0]:",
                "\t(0, 0): BEGIN",
                "\t(0, 1): NONDET 1",
                "\t(0, 2): TCREATE(1:\"srv\")",
                "\t(0, 3): TCREATE(2:\"c1\")",
                "\t(0, 4): TCREATE(3:\"c2\")",
                "\t(0, 5): TJOIN(1)",
                "\t(0, 6): TJOIN(2)",
                "\t(0, 7): TJOIN(3)",
                "\t(0, 8): END",
                "thread \"srv\"[tid=1]:",
                "\t(1, 0): BEGIN",
                "\t(1, 1): RECV() [(3, 1) c2]",
                "\t(1, 2): RECV() [(2, 1) c1]",
                "\t(1, 3): END",
                "thread \"c1\"[tid=2]:",
                "\t(2, 0): BEGIN",
                "\t(2, 1): SEND(srv, {\"t\":\"A\",\"f\":[11]})",
                "\t(2, 2): END",
                "thread \"c2\"[tid=3]:",
                "\t(3, 0): BEGIN",
                "\t(3, 1): SEND(srv, {\"t\":\"A\",\"f\":[2]})",
                "\t(3, 2): END");
    }

    @Test
    void tinyProgram_twoLineSpec_witnessFirstLineAndRecvsNameSends_derivedTextEqual() throws Exception {
        Path dir = WitnessSupport.fresh("unit-tiny");
        Path spec = WitnessSupport.spec(dir, TINY, "spec.jsonl", UnaryOperator.identity(), 2);
        List<String> specLines = Files.readAllLines(spec, StandardCharsets.UTF_8);
        assertEquals(2, specLines.size());
        assertEquals(L_1_2, specLines.get(1), "the kept line is the derived [A(1), A(2)]");

        // File mode: line 1 equals the launcher's witness: line, at the at: iteration.
        Path file = dir.resolve("w.txt");
        Out o = launch(dir, TINY, "must", "0", "1", "rep", "-", WitnessSupport.TIMEOUT, "--spec", spec.toString(),
                "--witness", file.toString());
        assertEquals(0, o.exit(), o.text());
        assertEquals("reports", field(o.stdout(), "verdict"));
        assertEquals("3 graphs (3 distinct summaries)", field(o.stdout(), "uncovered"));
        assertEquals("1 written to " + file, field(o.stdout(), "witnesses"));
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String first = text.substring(0, text.indexOf('\n'));
        assertEquals("uncovered summary (iteration " + atIteration(o.stdout()) + "): " + field(o.stdout(), "witness"),
                first);
        Witness w = WitnessSupport.parse(text);
        assertEquals(2, WitnessSupport.recvsNameSends(w), "srv's two receives name sends");

        // Directory mode: three files; the one of [A(2), A(11)] is the derived text.
        Path wdir = dir.resolve("wd");
        Out d = launch(dir, TINY, "must", "0", "1", "rep2", "-", WitnessSupport.TIMEOUT, "--spec", spec.toString(),
                "--witness", wdir + "/");
        assertEquals(0, d.exit(), d.text());
        List<String> texts = WitnessSupport.witnessFiles(wdir, 3);
        assertEquals(text, texts.get(0), "file mode wrote the first of directory mode's witnesses");
        int matched = 0;
        int lastIteration = 0;
        for (String t : texts) {
            Witness x = WitnessSupport.parse(t);
            assertEquals(2, WitnessSupport.recvsNameSends(x));
            assertTrue(x.iteration() > lastIteration && x.iteration() <= 4, "iterations increase in n: " + t);
            lastIteration = x.iteration();
            if (x.summaryLine().equals(L_2_11)) {
                assertEquals(derived(x.iteration()), t);
                matched++;
            }
        }
        assertEquals(1, matched, "one witness of [A(2), A(11)]");
    }
}
