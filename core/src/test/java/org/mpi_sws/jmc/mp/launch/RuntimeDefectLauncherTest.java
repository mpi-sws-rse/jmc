package org.mpi_sws.jmc.mp.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runtime defects seen through the launcher, each in a fresh JVM: a receive guard that throws or
 * calls the runtime, a program whose constructor fails, and a failing process that main never
 * awaits. The launcher's own time limit is 20 s; {@link MpLauncherTest#launch} fails a run that
 * has not ended within 180 s, which is how a hang shows.
 */
class RuntimeDefectLauncherTest {

    private static final String LAUNCHER = "org.mpi_sws.jmc.mp.cli.MpLauncher";
    private static final String FAULTS = "org.mpi_sws.jmc.mp.programs.faults.";
    private static final String DIR = "build/runtime-defects/";

    private static MpLauncherTest.Out run(String program, String strategy, String iterations, String tag,
            boolean export) throws Exception {
        Path exp = Path.of(DIR + tag + ".jsonl");
        Files.createDirectories(exp.getParent());
        Files.deleteIfExists(exp);
        return MpLauncherTest.launch(LAUNCHER, FAULTS + program, strategy, iterations, "1",
                DIR + "report-" + tag, export ? exp.toString() : "-", "20");
    }

    // ---------------------------------------------------------------- receive guards

    @Test
    void guardControl_must_passes() throws Exception {
        MpLauncherTest.Out o = run("GuardFaults:none", "must", "0", "g-none", false);
        assertEquals(0, o.exit(), o.text());
    }

    @Test
    void guardThrows_must_reportsTheGuardsException() throws Exception {
        MpLauncherTest.Out o = run("GuardFaults:throws", "must", "0", "g-throws", false);
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("guard boom"), o.text());
        assertTrue(o.text().contains("the receive guard of process"), o.text());
    }

    @Test
    void guardThrowsAnError_must_reportsTheGuardsError() throws Exception {
        MpLauncherTest.Out o = run("GuardFaults:error", "must", "0", "g-error", false);
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("guard assertion"), o.text());
        assertTrue(o.text().contains("the receive guard of process"), o.text());
    }

    @Test
    void guardCallsSelf_must_runEndsWithAFault() throws Exception {
        MpLauncherTest.Out o = run("GuardFaults:self", "must", "0", "g-self", false);
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("the receive guard of process"), o.text());
        assertTrue(o.text().contains("No current task."), o.text());
    }

    @Test
    void guardSends_must_runEndsWithAFault() throws Exception {
        MpLauncherTest.Out o = run("GuardFaults:send", "must", "0", "g-send", false);
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("the receive guard of process"), o.text());
        assertTrue(o.text().contains("No current task."), o.text());
    }

    @Test
    void guardSendsToACapturedId_must_runEndsWithAFault() throws Exception {
        MpLauncherTest.Out o = run("GuardFaults:sendCaptured", "must", "0", "g-send-captured", false);
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("the receive guard of process"), o.text());
        assertTrue(o.text().contains("No current task."), o.text());
    }

    @Test
    void guardThrows_mpRandom_runEndsWithTheGuardsException() throws Exception {
        MpLauncherTest.Out o = run("GuardFaults:throws", "mp-random", "20", "g-throws-r", false);
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("guard boom"), o.text());
    }

    // ---------------------------------------------------------------- a failing constructor

    @Test
    void constructorThrows_direct_causeAndItsFrameShown() throws Exception {
        MpLauncherTest.Out o = run("ConstructorFaults:direct", "must", "0", "c-direct", false);
        assertEquals(2, o.exit(), o.text());
        assertTrue(o.text().contains("bad size 0"), o.text());
        assertTrue(o.text().contains("at org.mpi_sws.jmc.mp.programs.faults.ConstructorFaults.<init>"), o.text());
    }

    @Test
    void constructorThrows_nested_rootCauseShown() throws Exception {
        MpLauncherTest.Out o = run("ConstructorFaults:nested", "must", "0", "c-nested", false);
        assertEquals(2, o.exit(), o.text());
        assertTrue(o.text().contains("caused by: java.lang.IllegalStateException: static init boom"), o.text());
        assertTrue(o.text().contains("usage: MpLauncher"), o.text());
    }

    // ---------------------------------------------------------------- an un-awaited failing process

    @Test
    void unawaitedFailure_must_assertionTextReported() throws Exception {
        MpLauncherTest.Out o = run("UnawaitedFailure", "must", "0", "u-must", false);
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("a's own assertion"), o.text());
    }

    @Test
    void unawaitedFailure_mustExport_assertionTextReported() throws Exception {
        MpLauncherTest.Out o = run("UnawaitedFailure", "must", "0", "u-must-x", true);
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("a's own assertion"), o.text());
    }

    /**
     * Under mp-random with seed 1, the first failing iteration is one where main returns before
     * {@code a} reaches its assertion. The checker does not run processes after main's end, so the
     * run fails with the live-process refusal, and {@code a}'s assertion, which never executes, is
     * not reported.
     */
    @Test
    void unawaitedFailure_mpRandom_liveProcessRefused() throws Exception {
        MpLauncherTest.Out o = run("UnawaitedFailure", "mp-random", "20", "u-random", false);
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("main returned while process(es) [3] were still live"), o.text());
    }
}
