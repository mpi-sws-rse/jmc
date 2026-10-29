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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tester tests for J1 round 01, claims C1 (launcher export bytes), C2(b) (blocked iterations are
 * exported), C4 (an errored iteration is not exported; {@code aborted}) and C6 (recorder refusals),
 * each through a fresh launcher JVM ({@link MpLauncherTest#launch}). Expected files are written by
 * hand from plan/jmc-must/SUMMARY-FORMAT.md.
 */
class MpLauncherExportTest {

    private static final String LAUNCHER = "org.mpi_sws.jmc.mp.launch.MpLauncher";
    private static final String J1 = "org.mpi_sws.jmc.mp.programs.j1.";
    private static final String DIR = "build/test-results/j1-tester/export/";

    private static final String M1 = "{\"t\":\"Msg\",\"f\":[1]}";
    private static final String M2 = "{\"t\":\"Msg\",\"f\":[2]}";
    private static final String M3 = "{\"t\":\"Msg\",\"f\":[3]}";
    private static final String ST3 = "\"statuses\":{\"t1\":\"done\",\"t2\":\"done\",\"t3\":\"done\"}";
    private static final String W12 =
            "\"t1\":[{\"s\":" + M1 + "},{\"s\":" + M2 + "}],\"t2\":[{\"r\":" + M2 + "},{\"s\":" + M3 + "}]";
    private static final String C1_HEADER = "{\"format\":1,\"tool\":\"jmc\",\"program\":\"must-1.3\",\"size\":\"\","
            + "\"visible\":[\"t1\",\"t2\",\"t3\"],\"model\":\"p2p\",\"enumeration\":\"sampled\",\"seed\":20261026,"
            + "\"iterations\":2000,\"encoding\":1}";
    private static final String C1_13 = "{" + ST3 + ",\"words\":{" + W12 + ",\"t3\":[{\"r\":" + M1 + "},{\"r\":" + M3
            + "}]},\"counts\":[0,0,0,0,2,0,2,0,1,0,2,2],\"n\":1978}";
    private static final String C1_31 = "{" + ST3 + ",\"words\":{" + W12 + ",\"t3\":[{\"r\":" + M3 + "},{\"r\":" + M1
            + "}]},\"counts\":[0,0,0,0,2,0,2,0,2,2,2,2],\"n\":22}";

    private static final String V1 = "{\"t\":\"V\",\"f\":[1]}";
    private static final String RS_DONE = "{\"statuses\":{\"r\":\"done\",\"s\":\"done\"},\"words\":{\"r\":[{\"r\":" + V1
            + "}],\"s\":[{\"s\":" + V1 + "}]},\"counts\":[1,0],\"n\":";
    private static final String RS_BLOCKED = "{\"statuses\":{\"r\":\"blocked\",\"s\":\"done\"},\"words\":{\"r\":[{\"r\":"
            + V1 + "}],\"s\":[{\"s\":" + V1 + "}]},\"counts\":[1,0],\"n\":";

    private static final Pattern N_FIELD = Pattern.compile(",\"n\":(\\d+)}$");

    private static List<String> export(String program, int iters, long seed, String file, int expectExit)
            throws Exception {
        Path p = Path.of(DIR + file);
        Files.deleteIfExists(p);
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, program, "mp-random", String.valueOf(iters),
                String.valueOf(seed), DIR + "report-" + file, p.toString());
        assertEquals(expectExit, o.exit(), "exit status\n" + o.text());
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        byte[] bytes = Files.readAllBytes(p);
        System.out.println("FILE " + p + " (" + bytes.length + " bytes)");
        for (String l : lines) {
            System.out.println("FILE> " + l);
        }
        return lines;
    }

    private static int sumN(List<String> lines) {
        int s = 0;
        for (String l : lines.subList(1, lines.size())) {
            Matcher m = N_FIELD.matcher(l);
            assertTrue(m.find(), "n field: " + l);
            s += Integer.parseInt(m.group(1));
        }
        return s;
    }

    private static void mutant(String what, MpLauncherTest.Executable e) {
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

    // ------------------------------------------------------------------ C1

    static void checkC1(String header, String l13, String l31) throws Exception {
        List<String> lines = export("org.mpi_sws.jmc.mp.programs.MustSection13", 2000, 20261026L, "c1.jsonl", 0);
        assertEquals(3, lines.size(), "header + two lines");
        assertEquals(header, lines.get(0), "header");
        assertEquals(new TreeSet<>(List.of(l13, l31)), new TreeSet<>(lines.subList(1, 3)), "summary lines");
        assertEquals(2000, sumN(lines), "n sums to the iterations exported");
        byte[] all = Files.readAllBytes(Path.of(DIR + "c1.jsonl"));
        String expectedBytes = header + "\n" + lines.get(1) + "\n" + lines.get(2) + "\n";
        assertEquals(expectedBytes, new String(all, StandardCharsets.UTF_8), "bytes: LF line ends, final LF");
    }

    @Test
    void c1_launcherExport_golden() throws Exception {
        checkC1(C1_HEADER, C1_13, C1_31);
    }

    @Test
    void c1_launcherExport_detects_mutatedHeader() {
        mutant("C1 header", () -> checkC1(C1_HEADER.replace("[\"t1\",\"t2\",\"t3\"]", "[\"t3\",\"t2\",\"t1\"]"),
                C1_13, C1_31));
    }

    @Test
    void c1_launcherExport_detects_mutatedWord() {
        mutant("C1 word", () -> checkC1(C1_HEADER, C1_13.replace("{\"r\":" + M2 + "}", "{\"r\":" + M2 + ",\"src\":\"t1\"}"),
                C1_31));
    }

    // ------------------------------------------------------------------ C2(b): blocked iterations exported

    @Test
    void c2b_launcherExport_blockedIterationsExported() throws Exception {
        List<String> lines = export(J1 + "BlockedAfterOne", 20, 1L, "blocked.jsonl", 0);
        assertEquals(List.of("{\"format\":1,\"tool\":\"jmc\",\"program\":\"blocked-after-one\",\"size\":\"\","
                + "\"visible\":[\"r\",\"s\"],\"model\":\"p2p\",\"enumeration\":\"sampled\",\"seed\":1,"
                + "\"iterations\":20,\"encoding\":1}", RS_BLOCKED + "20}"), lines);
    }

    @Test
    void c2b_launcherExport_detects_doneStatus() {
        mutant("C2b status", () -> {
            List<String> lines = export(J1 + "BlockedAfterOne", 20, 1L, "blocked.jsonl", 0);
            assertEquals(RS_BLOCKED.replace("\"blocked\"", "\"done\"") + "20}", lines.get(1));
        });
    }

    // ------------------------------------------------------------------ C4: errored iteration not exported

    static final String C4_HEADER = "{\"format\":1,\"tool\":\"jmc\",\"program\":\"asserts-at-3\",\"size\":\"\","
            + "\"visible\":[\"r\",\"s\"],\"model\":\"p2p\",\"enumeration\":\"aborted\",\"encoding\":1}";

    static void checkC4(int iters, int expectN) throws Exception {
        List<String> lines = export(J1 + "AssertsInMainAt3", iters, 1L, "c4-" + iters + ".jsonl", 1);
        // round 02 (F-J1-3): seed and iterations only in sampled files
        assertEquals(C4_HEADER, lines.get(0), "header");
        assertEquals(List.of(RS_DONE + expectN + "}"), lines.subList(1, lines.size()), "lines");
    }

    @Test
    void c4_errorInIteration3_of10_notExported() throws Exception {
        checkC4(10, 2);
    }

    @Test
    void c4_errorInLastIteration_notExported() throws Exception {
        checkC4(3, 2);
    }

    @Test
    void c4_detects_seedInAbortedHeader() {
        mutant("C4 header", () -> {
            List<String> lines = export(J1 + "AssertsInMainAt3", 10, 1L, "c4-10.jsonl", 1);
            assertEquals(C4_HEADER.replace(",\"encoding\":1}", ",\"seed\":1,\"iterations\":3,\"encoding\":1}"),
                    lines.get(0));
        });
    }

    @Test
    void c4_detects_errorIterationCounted() {
        mutant("C4", () -> checkC4(10, 3));
    }

    // ------------------------------------------------------------------ C6: recorder refusals

    static void checkRefused(String program, String why) throws Exception {
        Path file = Path.of(DIR + "c6.jsonl");
        Files.deleteIfExists(file);
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, program, "mp-random", "5", "1",
                DIR + "report-c6", file.toString());
        assertEquals(1, o.exit(), "exit status\n" + o.text());
        Matcher m = Pattern.compile("(?m)^FAILED:\\s+(.*)$").matcher(o.text());
        assertTrue(m.find(), "a FAILED line");
        assertTrue(m.group(1).contains("mp-recording:"), "FAILED names mp-recording: " + m.group(1));
        assertTrue(m.group(1).contains(why), "FAILED says " + why + ": " + m.group(1));
        // round 04: accounting -- the refused iteration is the first and nothing is exported
        assertTrue(o.text().contains("exported:   0 iterations as 0 distinct summaries (aborted, 1 not exported)"),
                "exported accounting\n" + o.text());
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1, lines.size(), "header only: " + lines);
        assertTrue(lines.get(0).contains("\"enumeration\":\"aborted\",\"encoding\":1}"), lines.get(0));
    }

    @Test
    void c6_tooManyProcesses_refused() throws Exception {
        // J2c round 02 (owner's ruling Q-J2c-3, roles by name): the second process carries role s,
        // which the declared list [r] does not contain
        checkRefused(J1 + "TooManyProcesses", "process 3 has role s, which is not declared [r]");
    }

    @Test
    void c6_visibleRoleNeverStarts_refused() throws Exception {
        checkRefused(J1 + "VisibleNeverStarts", "never spawned");
    }

    @Test
    void c6_detects_wellFormedProgram() {
        mutant("C6 control", () -> checkRefused("org.mpi_sws.jmc.mp.programs.MustSection13", "mp-recording"));
    }

    // ------------------------------------------------------------------ round 04: shape refusals

    private static final String MAIN_SEND = "main performs a send";
    private static final String MAIN_RECV = "main performs a receive";
    private static final String AFTER_AWAIT = "was spawned after main's first await";
    private static final String BY_PROCESS = "was spawned by process";
    private static final String AWAITS = "awaits another process";

    @Test
    void shape_mainRelay_refused() throws Exception {
        checkRefused(J1 + "MainRelay", MAIN_RECV);
    }

    @Test
    void shape_mainSendsAfterJoin_refused() throws Exception {
        checkRefused(J1 + "MainSendsAfterJoin", MAIN_SEND);
    }

    @Test
    void shape_mainSendsBeforeJoin_refused() throws Exception {
        checkRefused(J1 + "MainSendsBeforeJoin", MAIN_SEND);
    }

    @Test
    void shape_mainRecvThenSpawn_refused() throws Exception {
        checkRefused(J1 + "MainRecvThenSpawn", MAIN_RECV);
    }

    @Test
    void shape_spawnFirstThenMainRecv_refused() throws Exception {
        checkRefused(J1 + "SpawnFirstThenMainRecv", MAIN_RECV);
    }

    @Test
    void shape_spawnAfterJoin_refused() throws Exception {
        checkRefused(J1 + "SpawnAfterJoin", AFTER_AWAIT);
    }

    @Test
    void shape_joinChainSpawn_refused() throws Exception {
        checkRefused(J1 + "JoinChainSpawn", AFTER_AWAIT);
    }

    @Test
    void shape_joinChainSpawnEarly_refused() throws Exception {
        checkRefused(J1 + "JoinChainSpawnEarly", AFTER_AWAIT);
    }

    @Test
    void shape_processSpawnsProcess_refused() throws Exception {
        checkRefused(J1 + "ProcessSpawnsProcess", BY_PROCESS);
    }

    @Test
    void shape_processAwaitsProcess_refused() throws Exception {
        checkRefused(J1 + "ProcessAwaitsProcess", AWAITS);
    }

    @Test
    void shape_detects_inShapeProgram_spawnBeforeJoin() {
        mutant("shape control", () -> checkRefused(J1 + "SpawnBeforeJoin", AFTER_AWAIT));
    }

    @Test
    void shape_detects_inShapeProgram_processRelay() {
        mutant("shape control", () -> checkRefused(J1 + "ProcessRelay", MAIN_RECV));
    }

    // ------------------------------------------------------------------ round 04: F1, assertion in a process

    static void checkF1(int iters, int expectN) throws Exception {
        List<String> lines = export(J1 + "AssertsInProcessAt3", iters, 1L, "f1-" + iters + ".jsonl", 1);
        assertEquals("{\"format\":1,\"tool\":\"jmc\",\"program\":\"asserts-in-process-at-3\",\"size\":\"\","
                + "\"visible\":[\"r\",\"s\"],\"model\":\"p2p\",\"enumeration\":\"aborted\",\"encoding\":1}",
                lines.get(0), "header");
        assertEquals(List.of(RS_DONE + expectN + "}"), lines.subList(1, lines.size()), "lines");
    }

    @Test
    void f1_errorInProcess_iteration3_of10_notExported() throws Exception {
        checkF1(10, 2);
    }

    @Test
    void f1_errorInProcess_lastIteration_notExported() throws Exception {
        checkF1(3, 2);
    }

    @Test
    void f1_detects_errorIterationCounted() {
        mutant("F1", () -> checkF1(10, 3));
    }

    // ------------------------------------------------------------------ round 05: M3, main awaits every process

    private static final String NEVER_AWAITED = "were never awaited by main";
    private static final String STILL_LIVE = "were still live";

    /**
     * One launcher run per seed, 1 iteration each: every run must be refused (exit 1, mp-recording or
     * the base's live-process rule, aborted, header only, 0 exported); returns how many runs were
     * refused by the recorder's M3 rule (schedules in which a had already terminated).
     */
    static int checkUnawaited(String program, int seeds) throws Exception {
        return checkUnawaited(program, seeds, null)[0];
    }

    /**
     * As above; with {@code processFailure} non-null, a FAILED line containing it (the owner's ruling
     * J2b Q1: a process failure is the iteration's error when it happens) is a third accepted outcome.
     *
     * @return {refused by M3, refused by the live-process rule, failed by the process failure}
     */
    static int[] checkUnawaited(String program, int seeds, String processFailure) throws Exception {
        int m3 = 0;
        int live = 0;
        int failed = 0;
        for (int seed = 1; seed <= seeds; seed++) {
            Path file = Path.of(DIR + "m3.jsonl");
            Files.deleteIfExists(file);
            MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, program, "mp-random", "1", String.valueOf(seed),
                    DIR + "report-m3", file.toString());
            assertEquals(1, o.exit(), "seed " + seed + " exit status\n" + o.text());
            Matcher m = Pattern.compile("(?m)^FAILED:\\s+(.*)$").matcher(o.text());
            assertTrue(m.find(), "seed " + seed + ": a FAILED line");
            String f = m.group(1);
            if (f.contains("mp-recording:") && f.contains(NEVER_AWAITED)) {
                m3++;
            } else if (f.contains(STILL_LIVE)) {
                live++;
            } else if (processFailure != null && f.contains(processFailure)) {
                failed++;
            } else {
                throw new AssertionFailedError("seed " + seed + ": unexpected FAILED: " + f);
            }
            assertTrue(o.text().contains("exported:   0 iterations as 0 distinct summaries (aborted, 1 not exported)"),
                    "seed " + seed + " accounting\n" + o.text());
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals(1, lines.size(), "seed " + seed + " header only: " + lines);
            assertTrue(lines.get(0).contains("\"enumeration\":\"aborted\",\"encoding\":1}"), lines.get(0));
        }
        System.out.println("M3 " + program + ": " + seeds + " seeds, refused by M3 " + m3
                + ", by the live-process rule " + live + ", failed by the process failure " + failed);
        return new int[] {m3, live, failed};
    }

    @Test
    void m3_unawaitedFailingProcess_refused() throws Exception {
        // J2b round 02 (owner's ruling Q1): a's failure is the iteration's error when it happens, so the
        // recorder's never-awaited refusal no longer gets the chance; the live-process rule still can.
        int[] r = checkUnawaited(J1 + "UnawaitedFailing", 16,
                "process 2 failed: java.lang.AssertionError: a: fails, never awaited");
        assertEquals(0, r[0], "M3's refusal is pre-empted by the process failure");
        assertTrue(r[2] > 0, "some schedule had a fail before main returned: the failure is the error");
    }

    @Test
    void m3_unawaitedNormalProcess_refused() throws Exception {
        int m3 = checkUnawaited(J1 + "UnawaitedNormal", 16);
        assertTrue(m3 > 0, "some schedule had a terminated before main returned and M3 refused it");
    }

    @Test
    void m3_detects_awaitAllNormal() {
        mutant("M3 control", () -> checkUnawaited(J1 + "AwaitAllNormal", 2));
    }

    private static final String AB_NB = "{\"statuses\":{\"a\":\"done\",\"b\":\"done\"},\"words\":{\"a\":[{\"r\":null}],"
            + "\"b\":[{\"r\":null}]},\"counts\":[0,0],\"n\":";

    @Test
    void m3_awaitAllFailing_assertionReachesChecker() throws Exception {
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, J1 + "AwaitAllFailingAt3", "mp-random", "10", "1",
                DIR + "report-m3c", DIR + "m3c.jsonl");
        assertEquals(1, o.exit(), "exit\n" + o.text());
        // J2b round 02 (owner's ruling Q1): the failure reaches the checker as the process failure, latched
        // when a finishes and handed to main on its next event, before join1's rethrow could run
        assertTrue(o.text().contains("FAILED:     process 2 failed: java.lang.AssertionError: a: fails in run 3"),
                o.text());
        List<String> lines = Files.readAllLines(Path.of(DIR + "m3c.jsonl"), StandardCharsets.UTF_8);
        assertEquals(List.of("{\"format\":1,\"tool\":\"jmc\",\"program\":\"AwaitAllFailingAt3\",\"size\":\"\","
                + "\"visible\":[\"a\",\"b\"],\"model\":\"p2p\",\"enumeration\":\"aborted\",\"encoding\":1}",
                AB_NB + "2}"), lines);
    }

    @Test
    void m3_awaitAllNormal_sampled() throws Exception {
        List<String> lines = export(J1 + "AwaitAllNormal", 20, 1L, "m3n.jsonl", 0);
        assertEquals(List.of("{\"format\":1,\"tool\":\"jmc\",\"program\":\"AwaitAllNormal\",\"size\":\"\","
                + "\"visible\":[\"a\",\"b\"],\"model\":\"p2p\",\"enumeration\":\"sampled\",\"seed\":1,"
                + "\"iterations\":20,\"encoding\":1}", AB_NB + "20}"), lines);
    }

    private static final String LATE = "{\"statuses\":{\"a\":\"done\",\"b\":\"done\"},\"words\":{\"a\":[{\"s\":" + V1
            + "}],\"b\":[{\"r\":" + V1 + "}]},\"counts\":[0,1],\"n\":";

    @Test
    void m3_awaitLate_accepted() throws Exception {
        List<String> lines = export(J1 + "AwaitLate", 50, 1L, "m3l.jsonl", 0);
        assertEquals(List.of("{\"format\":1,\"tool\":\"jmc\",\"program\":\"AwaitLate\",\"size\":\"\","
                + "\"visible\":[\"a\",\"b\"],\"model\":\"p2p\",\"enumeration\":\"sampled\",\"seed\":1,"
                + "\"iterations\":50,\"encoding\":1}", LATE + "50}"), lines);
    }

    @Test
    void m3_detects_awaitLate_refusalExpected() {
        mutant("M3 await late", () -> checkUnawaited(J1 + "AwaitLate", 1));
    }
}
