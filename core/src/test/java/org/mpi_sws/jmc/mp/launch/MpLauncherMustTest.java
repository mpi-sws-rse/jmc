package org.mpi_sws.jmc.mp.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tester tests for J2 round 01, claim C5: the launcher's {@code must} export arm, through a fresh JVM.
 * Expected files written by hand (the §1.3 lines are my J1 golden lines, here with {@code n:1}).
 */
class MpLauncherMustTest {

    private static final String LAUNCHER = "org.mpi_sws.jmc.mp.launch.MpLauncher";
    private static final String DIR = "build/test-results/j2-tester/export/";

    private static final String M1 = "{\"t\":\"Msg\",\"f\":[1]}";
    private static final String M2 = "{\"t\":\"Msg\",\"f\":[2]}";
    private static final String M3 = "{\"t\":\"Msg\",\"f\":[3]}";
    private static final String ST3 = "\"statuses\":{\"t1\":\"done\",\"t2\":\"done\",\"t3\":\"done\"}";
    private static final String W12 =
            "\"t1\":[{\"s\":" + M1 + "},{\"s\":" + M2 + "}],\"t2\":[{\"r\":" + M2 + "},{\"s\":" + M3 + "}]";
    private static final String L13 = "{" + ST3 + ",\"words\":{" + W12 + ",\"t3\":[{\"r\":" + M1 + "},{\"r\":" + M3
            + "}]},\"counts\":[0,0,0,0,2,0,2,0,1,0,2,2],\"n\":1}";
    private static final String L31 = "{" + ST3 + ",\"words\":{" + W12 + ",\"t3\":[{\"r\":" + M3 + "},{\"r\":" + M1
            + "}]},\"counts\":[0,0,0,0,2,0,2,0,2,2,2,2],\"n\":1}";
    private static final String H13 = "{\"format\":1,\"tool\":\"jmc\",\"program\":\"must-1.3\",\"size\":\"\","
            + "\"visible\":[\"t1\",\"t2\",\"t3\"],\"model\":\"p2p\",\"enumeration\":\"complete\",\"encoding\":1}";

    record Exp(MpLauncherTest.Out out, List<String> lines) {}

    static Exp export(String program, String iterations, String file, int expectExit) throws Exception {
        Path p = Path.of(DIR + file);
        Files.deleteIfExists(p);
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, program, "must", iterations, "1",
                DIR + "report-" + file, p.toString(), "60");
        assertEquals(expectExit, o.exit(), "exit status\n" + o.text());
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        for (String l : lines) {
            System.out.println("FILE> " + l);
        }
        return new Exp(o, lines);
    }

    static String field(String text, String key) {
        Matcher m = Pattern.compile("(?m)^" + key + ":\\s+(.*)$").matcher(text);
        assertTrue(m.find(), "no " + key + ": line\n" + text);
        return m.group(1).trim();
    }

    static void mutant(String what, MpLauncherTest.Executable e) {
        AssertionFailedError err = assertThrows(AssertionFailedError.class, () -> {
            try {
                e.run();
            } catch (AssertionFailedError a) {
                throw a;
            } catch (Exception x) {
                throw new RuntimeException(x);
            }
        });
        System.out.println("MUTANT VERDICT (" + what + "): " + err.getMessage().split("\n")[0]);
    }

    static void checkC13(String header, Set<String> lines) throws Exception {
        Exp e = export("org.mpi_sws.jmc.mp.programs.MustSection13", "0", "c13.jsonl", 0);
        assertEquals(header, e.lines().get(0), "header");
        assertEquals(new TreeSet<>(lines), new TreeSet<>(e.lines().subList(1, e.lines().size())), "lines");
        String g = field(e.out().text(), "graphs");
        assertTrue(g.contains("2 distinct complete") && g.contains("0 distinct blocked") && g.contains("exhausted"), g);
    }

    @Test
    void c5_mustSection13_complete() throws Exception {
        checkC13(H13, Set.of(L13, L31));
    }

    @Test
    void c5_detects_mustSection13_sampledHeader() {
        mutant("C5 header", () -> checkC13(H13.replace("\"complete\"", "\"sampled\""), Set.of(L13, L31)));
    }

    @Test
    void c5_detects_mustSection13_mpRandomMultiplicity() {
        mutant("C5 n", () -> checkC13(H13, Set.of(L13.replace("\"n\":1}", "\"n\":1978}"), L31)));
    }

    @Test
    void c5_bounded_nsr4_cap2() throws Exception {
        Exp e = export("org.mpi_sws.jmc.mp.programs.j2.NSR", "2", "bounded.jsonl", 0);
        assertEquals("{\"format\":1,\"tool\":\"jmc\",\"program\":\"ns+r\",\"size\":\"N=4\","
                + "\"visible\":[\"r\",\"s1\",\"s2\",\"s3\",\"s4\"],\"model\":\"p2p\",\"enumeration\":\"bounded\","
                + "\"encoding\":1}", e.lines().get(0), "header");
        assertEquals(3, e.lines().size(), "header + 2 lines");
    }

    @Test
    void c5_complete_nsr4_uncapped_4lines() throws Exception {
        Exp e = export("org.mpi_sws.jmc.mp.programs.j2.NSR", "0", "nsr4.jsonl", 0);
        assertTrue(e.lines().get(0).contains("\"enumeration\":\"complete\""), e.lines().get(0));
        assertEquals(5, e.lines().size(), "header + 4 lines");
    }

    @Test
    void c5_detects_bounded_expectedComplete() {
        mutant("C5 bounded", () -> {
            Exp e = export("org.mpi_sws.jmc.mp.programs.j2.NSR", "2", "bounded.jsonl", 0);
            assertTrue(e.lines().get(0).contains("\"enumeration\":\"complete\""), e.lines().get(0));
        });
    }

    private static final String V1 = "{\"t\":\"V\",\"f\":[1]}";
    private static final String V2 = "{\"t\":\"V\",\"f\":[2]}";

    @Test
    void c5_aborted_assertionInRun2() throws Exception {
        Exp e = export("org.mpi_sws.jmc.mp.programs.j2.MustAssertsInRun2", "0", "aborted.jsonl", 1);
        assertTrue(e.out().text().contains("FAILED:"), "FAILED line");
        assertEquals("{\"format\":1,\"tool\":\"jmc\",\"program\":\"must-asserts-in-run-2\",\"size\":\"\","
                + "\"visible\":[\"r\",\"s1\",\"s2\"],\"model\":\"p2p\",\"enumeration\":\"aborted\",\"encoding\":1}",
                e.lines().get(0), "header");
        // derived: the first graph explored has r read s1's V(1) (least tid among the options)
        assertEquals(List.of("{\"statuses\":{\"r\":\"done\",\"s1\":\"done\",\"s2\":\"done\"},\"words\":{\"r\":[{\"r\":" + V1
                + "}],\"s1\":[{\"s\":" + V1 + "}],\"s2\":[{\"s\":" + V2 + "}]},\"counts\":[1,0,0,0,0,0],\"n\":1}"),
                e.lines().subList(1, e.lines().size()), "the run-1 line only");
    }

    @Test
    void c5_blocked_exportedComplete() throws Exception {
        Exp e = export("org.mpi_sws.jmc.mp.programs.j1.BlockedAlone", "0", "blocked.jsonl", 0);
        assertEquals(List.of("{\"format\":1,\"tool\":\"jmc\",\"program\":\"blocked-alone\",\"size\":\"\","
                + "\"visible\":[\"r\"],\"model\":\"p2p\",\"enumeration\":\"complete\",\"encoding\":1}",
                "{\"statuses\":{\"r\":\"blocked\"},\"words\":{\"r\":[]},\"counts\":[],\"n\":1}"), e.lines());
    }

    @Test
    void c5_detects_blocked_doneStatus() {
        mutant("C5 blocked status", () -> {
            Exp e = export("org.mpi_sws.jmc.mp.programs.j1.BlockedAlone", "0", "blocked.jsonl", 0);
            assertEquals("{\"statuses\":{\"r\":\"done\"},\"words\":{\"r\":[]},\"counts\":[],\"n\":1}", e.lines().get(1));
        });
    }

    @Test
    void c5_mpRandomStillRejectsZeroIterations() throws Exception {
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, "org.mpi_sws.jmc.mp.programs.MustSection13",
                "mp-random", "0", "1", DIR + "report-zero");
        assertEquals(2, o.exit(), "iterations 0 is allowed for must only\n" + o.text());
    }

    // ------------------------------------------------------------------ round 03 (reviewer majors M1, M3, M4)

    @Test
    void r3_m1_earlyNondet_aborted_oneLine() throws Exception {
        Exp e = export("org.mpi_sws.jmc.mp.programs.j2.R3EarlyNondet", "0", "r3-early.jsonl", 1);
        assertTrue(e.out().text().contains("FAILED:") && e.out().text().contains("replay drift"), e.out().text());
        assertEquals("{\"format\":1,\"tool\":\"jmc\",\"program\":\"r3-early-nondet\",\"size\":\"\","
                + "\"visible\":[\"p\"],\"model\":\"p2p\",\"enumeration\":\"aborted\",\"encoding\":1}",
                e.lines().get(0), "header");
        // derived: the first graph only (p's nondet is invisible: empty word, no counts)
        assertEquals(List.of("{\"statuses\":{\"p\":\"done\"},\"words\":{\"p\":[]},\"counts\":[],\"n\":1}"),
                e.lines().subList(1, e.lines().size()), "the first graph's line only");
    }

    @Test
    void r3_m3_cdSend_aborted_headerOnly() throws Exception {
        Exp e = export("org.mpi_sws.jmc.mp.programs.j2.R3Model", "0", "r3-cd.jsonl", 1);
        assertTrue(e.out().text().contains("FAILED:") && e.out().text().contains("which must does not support"),
                e.out().text());
        assertTrue(e.lines().get(0).contains("\"enumeration\":\"aborted\""), e.lines().get(0));
        assertEquals(1, e.lines().size(), "header only");
    }

    @Test
    void r3_m4_duplicateRoles_refusedByLauncher() throws Exception {
        Path p = Path.of(DIR + "r3-dup.jsonl");
        Files.deleteIfExists(p);
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, "org.mpi_sws.jmc.mp.programs.j2.R3DupRoles", "must",
                "0", "1", DIR + "report-r3-dup", p.toString(), "60");
        System.out.println("R3 M4 launcher exit " + o.exit() + "\n" + o.text());
        assertTrue(o.exit() != 0, "non-zero exit\n" + o.text());
        assertTrue(o.text().contains("roles are not distinct"), o.text());
        assertTrue(!Files.exists(p), "no export file");
    }
}
