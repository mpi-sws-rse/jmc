package org.mpi_sws.jmc.mp.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tester tests for J0 round 01, claim C3: {@link MpLauncher} runs one check per JVM with no agent,
 * prints ran/blocked/completed/elapsed/rate, and exits 0 on success, 1 when the checker fails, 2 on
 * bad arguments. Each case launches a fresh JVM ({@code java -cp <this test JVM's classpath>}, no
 * {@code -javaagent}) and reads its exit status and output.
 */
class MpLauncherTest {

    private static final String LAUNCHER = "org.mpi_sws.jmc.mp.launch.MpLauncher";
    private static final String MUT_LAUNCHER = "org.mpi_sws.jmc.mp.launch.MpLauncherMutCompleted";
    private static final String PROGRAM = "org.mpi_sws.jmc.mp.programs.MustSection13";
    private static final String MUTANTS = "org.mpi_sws.jmc.mp.programs.mutants.";
    private static final String REPORT = "build/test-results/j0-tester/launch";

    record Out(int exit, String text) {}

    static Out launch(String main, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(main);
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Thread pump = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                in.transferTo(buf);
            } catch (Exception ignored) {
                // process ended
            }
        });
        pump.start();
        if (!p.waitFor(180, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            pump.join(5000);
            fail("launcher did not terminate within 180 s: " + String.join(" ", args) + "\n" + buf);
        }
        pump.join(5000);
        String text = buf.toString(StandardCharsets.UTF_8);
        System.out.println("LAUNCH " + main + " " + String.join(" ", args) + " -> exit " + p.exitValue());
        for (String line : text.split("\n")) {
            if (!line.contains(" INFO ") && !line.contains("invalid element")) {
                System.out.println("OUT  " + line);
            }
        }
        return new Out(p.exitValue(), text);
    }

    private static Map<String, String> fields(String text) {
        Map<String, String> f = new HashMap<>();
        Matcher m = Pattern.compile("(?m)^(ran|blocked|completed|elapsed|rate|observed|FAILED|error):\\s+(.*)$")
                .matcher(text);
        while (m.find()) {
            f.putIfAbsent(m.group(1), m.group(2).trim());
        }
        return f;
    }

    private static double num(Map<String, String> f, String key, String text) {
        String v = f.get(key);
        if (v == null) {
            fail("no '" + key + ":' line in\n" + text);
        }
        return Double.parseDouble(v.split("\\s+")[0]);
    }

    /** The numbers of a successful run agree with each other and with the request. */
    static void checkNumbers(String launcher, String program, int requested, int expectedBlocked) throws Exception {
        Out o = launch(launcher, program, "mp-random", String.valueOf(requested), "20261026", REPORT);
        assertEquals(0, o.exit(), "exit status\n" + o.text());
        Map<String, String> f = fields(o.text());
        int ran = (int) num(f, "ran", o.text());
        int blocked = (int) num(f, "blocked", o.text());
        int completed = (int) num(f, "completed", o.text());
        double elapsed = num(f, "elapsed", o.text());
        double rate = num(f, "rate", o.text());
        assertEquals(requested, ran, "ran == requested (nothing failed)");
        assertEquals(expectedBlocked, blocked, "blocked");
        assertEquals(ran - blocked, completed, "completed == ran - blocked");
        assertTrue(elapsed > 0, "elapsed > 0");
        double expectRate = ran / elapsed;
        // elapsed is printed to the millisecond: allow that rounding plus 1 %.
        double tol = Math.abs(ran / (elapsed - 0.0005) - ran / (elapsed + 0.0005)) + 0.01 * expectRate;
        assertTrue(Math.abs(rate - expectRate) <= tol, "rate " + rate + " vs ran/elapsed " + expectRate);
        int checks = o.text().split("JMC checker started", -1).length - 1;
        assertEquals(1, checks, "exactly one JmcModelChecker.check (log line 'JMC checker started')");
        Matcher t = Pattern.compile("t3 read 1 first: (\\d+); 3 first: (\\d+)(?:; other first: (\\d+))?")
                .matcher(f.getOrDefault("observed", ""));
        assertTrue(t.find(), "observed line");
        int other = t.group(3) == null ? 0 : Integer.parseInt(t.group(3));
        assertEquals(0, other, "observed: other first");
        assertEquals(completed, Integer.parseInt(t.group(1)) + Integer.parseInt(t.group(2)) + other,
                "observed tally == completed iterations");
    }

    private static void mutant(Executable e) {
        AssertionFailedError err = assertThrows(AssertionFailedError.class, () -> {
            try {
                e.run();
            } catch (AssertionFailedError a) {
                throw a;
            } catch (Exception x) {
                throw new RuntimeException(x);
            }
        });
        System.out.println("MUTANT VERDICT (C3): " + err.getMessage().split("\n")[0]);
    }

    interface Executable {
        void run() throws Exception;
    }

    // ------------------------------------------------------------------ success runs

    @Test
    void numbers_mustSection13() throws Exception {
        checkNumbers(LAUNCHER, PROGRAM, 500, 0);
    }

    @Test
    void numbers_mustSection13_detects_T3RecvThrice() {
        mutant(() -> checkNumbers(LAUNCHER, MUTANTS + "T3RecvThrice", 200, 0));
    }

    @Test
    void numbers_blockedProgram() throws Exception {
        checkNumbers(LAUNCHER, MUTANTS + "T3RecvThrice", 200, 200);
    }

    @Test
    void numbers_blockedProgram_detects_mutantLauncher() {
        mutant(() -> checkNumbers(MUT_LAUNCHER, MUTANTS + "T3RecvThrice", 200, 200));
    }

    // ------------------------------------------------------------------ exit 2: bad arguments

    private static void expectExit(int code, Out o) {
        assertEquals(code, o.exit(), "exit status; output:\n" + o.text());
    }

    /** Control: the same argument shape with a valid program exits 0, so the exit-2 checks discriminate. */
    private static void control() throws Exception {
        expectExit(0, launch(LAUNCHER, PROGRAM, "mp-random", "5", "1", REPORT));
    }

    @Test
    void exit2_noArguments_and_tooMany() throws Exception {
        expectExit(2, launch(LAUNCHER));
        // J1 round 02: six arguments are now valid (the 6th is the export file); seven are too many
        expectExit(2, launch(LAUNCHER, PROGRAM, "mp-random", "5", "1", REPORT, REPORT + "/export.jsonl", "extra"));
        control();
    }

    @Test
    void exit2_notRunnable() throws Exception {
        expectExit(2, launch(LAUNCHER, "java.lang.Object", "mp-random", "5", "1", REPORT));
        control();
    }

    @Test
    void exit2_unknownClass() throws Exception {
        expectExit(2, launch(LAUNCHER, "no.such.Program", "mp-random", "5", "1", REPORT));
    }

    @Test
    void exit2_noPublicNoArgConstructor() throws Exception {
        expectExit(2, launch(LAUNCHER, MUTANTS + "NoPublicConstructor", "mp-random", "5", "1", REPORT));
    }

    @Test
    void exit2_nonNumericIterations() throws Exception {
        expectExit(2, launch(LAUNCHER, PROGRAM, "mp-random", "many", "1", REPORT));
    }

    @Test
    void exit2_nonNumericSeed() throws Exception {
        expectExit(2, launch(LAUNCHER, PROGRAM, "mp-random", "5", "seed", REPORT));
    }

    @Test
    void exit2_zeroIterations() throws Exception {
        expectExit(2, launch(LAUNCHER, PROGRAM, "mp-random", "0", "1", REPORT));
    }

    @Test
    void exit2_negativeIterations() throws Exception {
        expectExit(2, launch(LAUNCHER, PROGRAM, "mp-random", "-5", "1", REPORT));
    }

    @Test
    void exit2_unknownStrategy() throws Exception {
        expectExit(2, launch(LAUNCHER, PROGRAM, "no-such-strategy", "5", "1", REPORT));
    }

    // ------------------------------------------------------------------ exit 1: the checker fails

    @Test
    void exit1_abandonedProcess() throws Exception {
        Out o = launch(LAUNCHER, MUTANTS + "AbandonsProcess", "mp-random", "5", "1", REPORT);
        expectExit(1, o);
        assertTrue(o.text().contains("FAILED:"), "a FAILED line");
        control();
    }

    @Test
    // F1 enabled in J1 round 04: MpProcess spawns over MpThread, which rethrows a process failure to the joiner.
    void exit1_assertionInProcess() throws Exception {
        Out o = launch(LAUNCHER, MUTANTS + "AssertsInProcess", "mp-random", "5", "1", REPORT);
        expectExit(1, o);
        assertTrue(o.text().contains("FAILED:"), "a FAILED line");
    }

    @Test
    void exit1_assertionInMain() throws Exception {
        Out o = launch(LAUNCHER, MUTANTS + "AssertsInMain", "mp-random", "5", "1", REPORT);
        expectExit(1, o);
        assertTrue(o.text().contains("FAILED:"), "a FAILED line");
    }

    @Test
    // F2 enabled in J1 round 01: the agreed fix (JmcModelChecker catches Throwable) is applied.
    void exit1_assertionInMain_onLastIteration() throws Exception {
        // One iteration: the failing iteration is also the last one.
        Out o = launch(LAUNCHER, MUTANTS + "AssertsInMain", "mp-random", "1", "1", REPORT);
        expectExit(1, o);
    }
}
