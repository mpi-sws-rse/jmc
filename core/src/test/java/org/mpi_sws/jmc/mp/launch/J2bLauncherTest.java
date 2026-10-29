package org.mpi_sws.jmc.mp.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tester tests for J2b round 01: the launcher's counting mode under {@code must} (export file
 * {@code -} or absent) for a plain {@link Runnable}, {@code mp-random} without export unchanged, and
 * the hidden-failure export (F4) under both strategies.
 */
class J2bLauncherTest {

    private static final String LAUNCHER = "org.mpi_sws.jmc.mp.launch.MpLauncher";
    private static final String PROGRAMS = "org.mpi_sws.jmc.mp.programs.j2b.J2bPrograms";
    private static final String DIR = "build/test-results/j2b-tester/launch/";

    @Test
    void counting_dash_plainRunnable() throws Exception {
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, PROGRAMS + ":f2_conditional_spawn", "must", "0", "1",
                DIR + "rep-dash", "-", "60");
        System.out.println(o.text());
        assertEquals(0, o.exit(), o.text());
        assertEquals("5 distinct complete, 0 distinct blocked; stack exhausted",
                MpLauncherMustTest.field(o.text(), "graphs"));
        assertTrue(!o.text().contains("exportFile:") && !o.text().contains("exported:"), o.text());
        assertTrue(!Files.exists(Path.of("-")), "no file named -");
    }

    @Test
    void counting_absent_plainRunnable() throws Exception {
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, PROGRAMS + ":f4_join_blocked", "must", "0", "1",
                DIR + "rep-absent");
        System.out.println(o.text());
        assertEquals(0, o.exit(), o.text());
        assertEquals("0 distinct complete, 1 distinct blocked; stack exhausted",
                MpLauncherMustTest.field(o.text(), "graphs"));
    }

    @Test
    void mpRandom_noExport_unchanged() throws Exception {
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, PROGRAMS + ":f3_spawn_edge", "mp-random", "10", "1",
                DIR + "rep-random");
        System.out.println(o.text());
        assertEquals(0, o.exit(), o.text());
        assertTrue(!o.text().contains("graphs:"), "no graphs line under mp-random");
        assertEquals("10 iterations", MpLauncherMustTest.field(o.text(), "ran"));
    }

    static List<String> exportFails(String strategy, String iterations) throws Exception {
        Path p = Path.of(DIR + "hidden-" + strategy + ".jsonl");
        Files.deleteIfExists(p);
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, "org.mpi_sws.jmc.mp.programs.j2b.HiddenFailure",
                strategy, iterations, "1", DIR + "rep-hidden-" + strategy, p.toString(), "60");
        System.out.println(o.text());
        assertEquals(1, o.exit(), o.text());
        // round 02 (owner's ruling Q1): the process failure itself is the iteration's error
        assertTrue(o.text().contains("FAILED:") && o.text().contains("process ")
                && o.text().contains("failed: java.lang.IllegalStateException: f fails"), o.text());
        List<String> lines = Files.readAllLines(p);
        assertTrue(lines.get(0).contains("\"enumeration\":\"aborted\""), lines.get(0));
        return lines;
    }

    @Test
    void hiddenFailure_mustExport_abortedNothing() throws Exception {
        assertEquals(1, exportFails("must", "0").size(), "header only");
    }

    @Test
    void hiddenFailure_mpRandomExport_abortedNothing() throws Exception {
        assertEquals(1, exportFails("mp-random", "5").size(), "header only");
    }

    // ------------------------------------------------------------------ round 04: J2b-F4 (no hang)

    private static final String NOT_MPTHREAD = "must: task 2 was not created by MpThread; the Must-API spawns only"
            + " MpThreads";

    /** Runs the launcher in a fresh JVM, killing it after {@code seconds}: a hang fails, it does not block. */
    static MpLauncherTest.Out launchBounded(long seconds, String... args) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(LAUNCHER);
        cmd.addAll(java.util.List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        Thread pump = new Thread(() -> {
            try (java.io.InputStream in = p.getInputStream()) {
                in.transferTo(buf);
            } catch (java.io.IOException e) {
                // the process ended
            }
        });
        pump.setDaemon(true);
        pump.start();
        boolean done = p.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly().waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        pump.join(5000);
        System.out.println("J2B F4 launcher (done=" + done + ")\n" + buf);
        assertTrue(done, "the launcher ended within " + seconds + " s (J2b-F4: no hang)\n" + buf);
        return new MpLauncherTest.Out(p.exitValue(), buf.toString());
    }

    @Test
    @org.junit.jupiter.api.Timeout(180)
    void f4b_nonMpThread_counting_failsNoHang() throws Exception {
        // SeqJtOk spawns JmcThreads: the first START (task 2) is refused, the run ends with exit 1
        MpLauncherTest.Out o = launchBounded(90, "org.mpi_sws.jmc.mp.programs.seq.SeqJtOk", "must", "0", "1",
                DIR + "rep-f4b-count", "-", "30");
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("FAILED:     " + NOT_MPTHREAD), o.text());
    }

    @Test
    @org.junit.jupiter.api.Timeout(180)
    void f4b_nonMpThread_export_failsNoHang() throws Exception {
        Path p = Path.of(DIR + "f4b-export.jsonl");
        Files.deleteIfExists(p);
        MpLauncherTest.Out o = launchBounded(90, "org.mpi_sws.jmc.mp.programs.j2b.JtExport", "must", "0", "1",
                DIR + "rep-f4b-export", p.toString(), "30");
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("FAILED:     " + NOT_MPTHREAD), o.text());
        List<String> lines = Files.readAllLines(p);
        assertEquals(1, lines.size(), "header only: " + lines);
        assertTrue(lines.get(0).contains("\"enumeration\":\"aborted\""), lines.get(0));
    }
}
