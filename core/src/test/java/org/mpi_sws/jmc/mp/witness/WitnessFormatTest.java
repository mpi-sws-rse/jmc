package org.mpi_sws.jmc.mp.witness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.TIMEOUT;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.atIteration;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.field;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.launch;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.lines;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.j8s.OnlineBank;
import org.mpi_sws.jmc.mp.cli.MpProgram;
import org.mpi_sws.jmc.mp.witness.WitnessSupport.Out;
import org.mpi_sws.jmc.mp.witness.WitnessSupport.Witness;

import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * WITNESS tester tests of the witness format, each witness derived by hand from its fixture before the
 * run: a receive reading ⊥ ({@code RECV() [TIMEOUT]}), threads ending blocked ({@code BLK}, no END; a
 * daemon printed by its path name {@code m/0} with the daemon marker; main blocked on its await of a blocked
 * process, so no join line), main's completed awaits as {@code TJOIN} lines in await order, every receive
 * naming the sender's thread, a
 * nondet in main before its creations (TCREATE at k 2) and in a process, invisible roles printed,
 * a SEND to main and to a daemon, values with nested records, lists, sets, enums (unit variants) and Tids; and the
 * an ASYN fixture, a role holding a quote and a comma (printed verbatim), and the consistency oracle
 * (the summary re-derived from the text equals line 1) over every witness of several runs.
 */
class WitnessFormatTest {

    /** Directory-mode run of {@code program} against a spec (header + {@code keep} lines, edited); the n files. */
    static List<String> witnesses(String name, String program, UnaryOperator<String> edit, int files, int... keep)
            throws Exception {
        Path dir = WitnessSupport.fresh("format-" + name);
        Path spec = WitnessSupport.spec(dir, program, "spec.jsonl", edit, keep);
        Path wd = dir.resolve("wd");
        Out o = launch(dir, program, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", spec.toString(), "--witness",
                wd + "/");
        assertEquals(0, o.exit(), o.text());
        assertEquals(files + " written to " + wd + "/", field(o.stdout(), "witnesses"));
        List<String> texts = WitnessSupport.witnessFiles(wd, files);
        Witness first = WitnessSupport.parse(texts.get(0));
        assertEquals(atIteration(o.stdout()), first.iteration());
        assertEquals(field(o.stdout(), "witness"), first.summaryLine());
        return texts;
    }

    @Test
    void timeout_receiveReadingBottom() throws Exception {
        // Two executions; the spec keeps the one where srv reads A(1) (body line 2), so the ⊥ one is uncovered.
        List<String> t = witnesses("timeout", WitnessSupport.PKG + "WitnessTimeout", UnaryOperator.identity(), 1, 2);
        int i = WitnessSupport.parse(t.get(0)).iteration();
        assertEquals(lines(
                "uncovered summary (iteration " + i + "): {\"statuses\":{\"c\":\"done\",\"srv\":\"done\"},\"words\":{\"c\":"
                        + "[{\"s\":{\"t\":\"A\",\"f\":[1]}}],\"srv\":[{\"r\":null}]},\"counts\":[0,0],\"n\":1}",
                "thread \"main\"[tid=0]:",
                "\t(0, 0): BEGIN",
                "\t(0, 1): TCREATE(1:\"srv\")",
                "\t(0, 2): TCREATE(2:\"c\")",
                "\t(0, 3): TJOIN(1)",
                "\t(0, 4): TJOIN(2)",
                "\t(0, 5): END",
                "thread \"srv\"[tid=1]:",
                "\t(1, 0): BEGIN",
                "\t(1, 1): RECV() [TIMEOUT]",
                "\t(1, 2): END",
                "thread \"c\"[tid=2]:",
                "\t(2, 0): BEGIN",
                "\t(2, 1): SEND(srv, {\"t\":\"A\",\"f\":[1]})",
                "\t(2, 2): END"), t.get(0));
        WitnessSupport.checkConsistent(t.get(0), List.of("srv", "c"));
    }

    @Test
    void blocked_daemonAndProcessAndMainEndBlocked_nondetInProcess_sendToDaemon() throws Exception {
        // The spec keeps w's done line (body line 2); the x = 1 execution is uncovered: w blocked, main blocked on
        // its await of w, the daemon m/0 blocked on its second receive after reading c's A(7).
        List<String> t = witnesses("blocked", WitnessSupport.PKG + "WitnessBlocked", UnaryOperator.identity(), 1, 2);
        int i = WitnessSupport.parse(t.get(0)).iteration();
        assertEquals(lines(
                "uncovered summary (iteration " + i + "): {\"statuses\":{\"w\":\"blocked\"},\"words\":{\"w\":[]},"
                        + "\"counts\":[],\"n\":1}",
                "thread \"main\"[tid=0]:",
                "\t(0, 0): BEGIN",
                "\t(0, 1): TCREATE(1:\"m/0\")",
                "\t(0, 2): TCREATE(2:\"w\")",
                "\t(0, 3): TCREATE(3:\"c\")",
                "\t(0, 4): BLK",
                "thread \"m/0\"[tid=1, daemon]:",
                "\t(1, 0): BEGIN",
                "\t(1, 1): RECV() [(3, 2) c]",
                "\t(1, 2): BLK",
                "thread \"w\"[tid=2]:",
                "\t(2, 0): BEGIN",
                "\t(2, 1): BLK",
                "thread \"c\"[tid=3]:",
                "\t(3, 0): BEGIN",
                "\t(3, 1): NONDET 1",
                "\t(3, 2): SEND(m/0, {\"t\":\"A\",\"f\":[7]})",
                "\t(3, 3): END"), t.get(0));
        WitnessSupport.checkConsistent(t.get(0), List.of("w"));
    }

    @Test
    void values_nestedRecordsListsSetsEnumsTids_sendToMain_wholeTextDerived() throws Exception {
        // One execution (iteration 1); the spec is its own line with RED replaced by BLUE, so it is uncovered.
        String w = "{\"t\":\"W\",\"f\":[{\"t\":\"In\",\"f\":[[3,1],[\"a\",\"b\"]]},\"srv\",true,"
                + "{\"t\":\"RED\",\"f\":[]}]}";
        String p = "{\"t\":\"P\",\"f\":[\"main\"]}";
        List<String> t = witnesses("values", WitnessSupport.PKG + "WitnessValues", l -> l.replace("RED", "BLUE"), 1, 1);
        assertEquals(lines(
                "uncovered summary (iteration 1): {\"statuses\":{\"c\":\"done\",\"srv\":\"done\"},\"words\":{\"c\":[{\"s\":"
                        + p + "},{\"s\":" + w + "}],\"srv\":[{\"r\":" + w + "}]},\"counts\":[0,0,2],\"n\":1}",
                "thread \"main\"[tid=0]:",
                "\t(0, 0): BEGIN",
                "\t(0, 1): TCREATE(1:\"srv\")",
                "\t(0, 2): TCREATE(2:\"c\")",
                "\t(0, 3): TJOIN(1)",
                "\t(0, 4): TJOIN(2)",
                "\t(0, 5): END",
                "thread \"srv\"[tid=1]:",
                "\t(1, 0): BEGIN",
                "\t(1, 1): RECV() [(2, 2) c]",
                "\t(1, 2): END",
                "thread \"c\"[tid=2]:",
                "\t(2, 0): BEGIN",
                "\t(2, 1): SEND(main, " + p + ")",
                "\t(2, 2): SEND(srv, " + w + ")",
                "\t(2, 3): END"), t.get(0));
        WitnessSupport.checkConsistent(t.get(0), List.of("srv", "c"));
    }

    @Test
    void asyn_twoSendsOfOneSenderReadInReverse() throws Exception {
        // ASYN: c's A(1), A(2) to srv are unordered; the spec keeps srv reading A(1) first, so the execution
        // reading A(2) first is uncovered. Counts (roles c, srv): c's sends have 0 srv events before; srv's
        // first receive has both c sends before (rf (2, 2) and po), its second both too.
        Path dir = WitnessSupport.fresh("format-asyn");
        String prog = WitnessSupport.PKG + "WitnessAsyn";
        String a1 = "{\"t\":\"A\",\"f\":[1]}";
        String a2 = "{\"t\":\"A\",\"f\":[2]}";
        Path spec = WitnessSupport.specWhere(dir, prog, "spec.jsonl",
                l -> l.contains("\"srv\":[{\"r\":" + a1 + "},{\"r\":" + a2 + "}]"));
        Path wd = dir.resolve("wd");
        Out o = launch(dir, prog, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", spec.toString(), "--witness", wd + "/");
        assertEquals(0, o.exit(), o.text());
        String t = WitnessSupport.witnessFiles(wd, 1).get(0);
        int i = WitnessSupport.parse(t).iteration();
        assertEquals(atIteration(o.stdout()), i);
        assertEquals(lines(
                "uncovered summary (iteration " + i + "): {\"statuses\":{\"c\":\"done\",\"srv\":\"done\"},\"words\":{\"c\":"
                        + "[{\"s\":" + a1 + "},{\"s\":" + a2 + "}],\"srv\":[{\"r\":" + a2 + "},{\"r\":" + a1 + "}]},"
                        + "\"counts\":[0,0,2,2],\"n\":1}",
                "thread \"main\"[tid=0]:",
                "\t(0, 0): BEGIN",
                "\t(0, 1): TCREATE(1:\"srv\")",
                "\t(0, 2): TCREATE(2:\"c\")",
                "\t(0, 3): TJOIN(1)",
                "\t(0, 4): TJOIN(2)",
                "\t(0, 5): END",
                "thread \"srv\"[tid=1]:",
                "\t(1, 0): BEGIN",
                "\t(1, 1): RECV() [(2, 2) c]",
                "\t(1, 2): RECV() [(2, 1) c]",
                "\t(1, 3): END",
                "thread \"c\"[tid=2]:",
                "\t(2, 0): BEGIN",
                "\t(2, 1): SEND(srv, " + a1 + ")",
                "\t(2, 2): SEND(srv, " + a2 + ")",
                "\t(2, 3): END"), t);
        WitnessSupport.checkConsistent(t, List.of("srv", "c"));
    }

    @Test
    void roleWithQuoteAndComma_writtenVerbatim() throws Exception {
        // Receiver s"v,w (visible); the spec keeps its A(0) line, so the x = 1 execution is uncovered. The
        // summary escapes the quote (JSON); the witness prints the name verbatim in the header, TCREATE and SEND.
        Path dir = WitnessSupport.fresh("format-quote");
        String prog = WitnessSupport.PKG + "WitnessOddRole:quote";
        Path spec = WitnessSupport.specWhere(dir, prog, "spec.jsonl", l -> l.contains("\"f\":[0]"));
        Path wd = dir.resolve("wd");
        Out o = launch(dir, prog, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", spec.toString(), "--witness", wd + "/");
        assertEquals(0, o.exit(), o.text());
        String t = WitnessSupport.witnessFiles(wd, 1).get(0);
        int i = WitnessSupport.parse(t).iteration();
        assertEquals(lines(
                "uncovered summary (iteration " + i + "): {\"statuses\":{\"s\\\"v,w\":\"done\"},\"words\":{\"s\\\"v,w\":"
                        + "[{\"r\":{\"t\":\"A\",\"f\":[1]}}]},\"counts\":[],\"n\":1}",
                "thread \"main\"[tid=0]:",
                "\t(0, 0): BEGIN",
                "\t(0, 1): TCREATE(1:\"s\"v,w\")",
                "\t(0, 2): TCREATE(2:\"c\")",
                "\t(0, 3): TJOIN(1)",
                "\t(0, 4): TJOIN(2)",
                "\t(0, 5): END",
                "thread \"s\"v,w\"[tid=1]:",
                "\t(1, 0): BEGIN",
                "\t(1, 1): RECV() [(2, 2) c]",
                "\t(1, 2): END",
                "thread \"c\"[tid=2]:",
                "\t(2, 0): BEGIN",
                "\t(2, 1): NONDET 1",
                "\t(2, 2): SEND(s\"v,w, {\"t\":\"A\",\"f\":[1]})",
                "\t(2, 3): END"), t);
        WitnessSupport.checkConsistent(t, List.of("s\"v,w"));
    }

    /** The oracle over every witness of a directory-mode run of {@code program} against {@code spec}; the count. */
    static int oracle(String name, String program, MpProgram p, Path dir, Path spec) throws Exception {
        Path wd = dir.resolve("wd-" + name);
        Out o = launch(dir, program, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", spec.toString(), "--witness",
                wd + "/");
        assertEquals(0, o.exit(), o.text());
        String u = field(o.stdout(), "uncovered");
        int distinct = Integer.parseInt(u.replaceAll("^\\d+ graphs \\((\\d+) distinct summaries\\)$", "$1"));
        assertEquals(distinct + " written to " + wd + "/", field(o.stdout(), "witnesses"));
        List<String> texts = WitnessSupport.witnessFiles(wd, distinct);
        for (String text : texts) {
            WitnessSupport.checkConsistent(text, p.visibleRoles());
        }
        System.out.println("WITNESS oracle " + name + ": " + distinct + " witnesses consistent");
        return distinct;
    }

    @Test
    void oracle_everyWitnessOfSeveralRuns_rederivedSummaryEqualsLine1() throws Exception {
        Path dir = WitnessSupport.fresh("format-oracle");
        String bank = WitnessSupport.BANK;
        // OnlineBank n 2 and n 4 against header + one line of their own exports: all other summaries uncovered.
        assertEquals(11, oracle("bank2", bank + ":2", new OnlineBank("2"), dir,
                WitnessSupport.spec(dir, bank + ":2", "bank2.jsonl", UnaryOperator.identity(), 2)));
        assertEquals(19, oracle("bank4", bank + ":4", new OnlineBank("4"), dir,
                WitnessSupport.spec(dir, bank + ":4", "bank4.jsonl", UnaryOperator.identity(), 3)));
        assertEquals(3, oracle("tiny", WitnessSupport.PKG + "WitnessTiny", new WitnessTiny(), dir,
                WitnessSupport.spec(dir, WitnessSupport.PKG + "WitnessTiny", "tiny.jsonl", UnaryOperator.identity(), 2)));
        // J4's A3Lock (two visible clients, non-trivial counts): the double-grant implementation against the
        // complete export of the specification.
        String a3 = "org.mpi_sws.jmc.mp.fixtures.apps.A3Lock:";
        Path a3spec = dir.resolve("a3-spec.jsonl");
        Out e = launch(dir, a3 + "spec,2,1", "must", "0", "1", "rep-a3", a3spec.toString(), TIMEOUT);
        assertEquals(0, e.exit(), e.text());
        int n = oracle("a3", a3 + "double-grant,2,1",
                new org.mpi_sws.jmc.mp.fixtures.apps.A3Lock("double-grant", "2", "1"), dir, a3spec);
        org.junit.jupiter.api.Assertions.assertTrue(n >= 2, "A3Lock double-grant has uncovered summaries: " + n);
    }
}
