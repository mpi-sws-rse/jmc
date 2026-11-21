package org.mpi_sws.jmc.mp.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The named form's exit statuses: 0 conforms or nothing to report, 1 a violation, 2 refused, 3 partial or
 * incomplete; and the positional form keeping its own (0 completed, 1 failed, 2 refused). Every case runs the
 * launcher in a fresh JVM with assertions enabled.
 */
class NamedExitCodesTest {

    private static final String CHOICE = NamedCliTest.Choice.class.getName();
    private static final String FAILS = FailsSecond.class.getName();

    /**
     * A client chooses 1 or 2 and sends it to main. Where the failure is: {@code process} the client asserts it did
     * not choose 2; {@code main} main asserts it did not receive 2; {@code boom}, {@code nomsg} and {@code recording}
     * main throws, on receiving 2, a {@code RuntimeException} with the message "boom", with no message, and with a
     * message that starts as the recording strategy's do; {@code guard} main's receive guard throws on 2;
     * {@code always} main fails in every execution, {@code processalways} the client does. {@code must} explores
     * the choice 1 first, so the failing execution is the second, and with two iterations the last one.
     */
    public static final class FailsSecond implements Runnable {
        private final String where;

        /**
         * Creates the program.
         *
         * @param args {@code process}, {@code main} or {@code always}
         */
        public FailsSecond(String... args) {
            this.where = args[0];
        }

        @Override
        public void run() {
            CommunicationModel m = CommunicationModel.P2P;
            Tid main = MpProcess.self();
            String w = where;
            MpProcess.Handle client = MpProcess.spawn("client", () -> {
                int n = Nondet.choose(List.of(1, 2));
                Channel.send(m, main, new Value(n));
                assert !w.equals("process") || n != 2 : "the client chose 2";
                assert !w.equals("processalways") : "the client always fails";
            });
            int got = w.equals("guard")
                    ? ((Value) Channel.recv(m, v -> {
                        if (((Value) v).n() == 2) {
                            throw new IllegalArgumentException("the guard threw on 2");
                        }
                        return true;
                    })).n()
                    : Channel.recv(m, Value.class).n();
            client.await();
            assert !w.equals("main") || got != 2 : "main received 2";
            assert !w.equals("always") : "main always fails";
            if (got == 2) {
                switch (w) {
                    case "boom" -> throw new RuntimeException("boom");
                    case "nomsg" -> throw new RuntimeException();
                    case "recording" -> throw new RuntimeException("mp-recording: ordinary program failure");
                    default -> { }
                }
            }
        }
    }

    /**
     * A value.
     *
     * @param n the value
     */
    @Tag("Value")
    public record Value(int n) implements MpMessage {}

    /** Seven processes choosing among ten values each: far too many executions to finish in a second. */
    public static final class Many implements Runnable {
        /** Creates the program. */
        public Many() {}

        @Override
        public void run() {
            List<MpProcess.Handle> hs = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                hs.add(MpProcess.spawn("p" + i, () -> Nondet.choose(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))));
            }
            hs.forEach(MpProcess.Handle::await);
        }
    }

    /** An MpProgram whose main sends a message itself, which the export format refuses. */
    public static final class MainSends implements MpProgram {
        /** Creates the program. */
        public MainSends() {}

        @Override
        public void run() {
            MpProcess.Handle server = MpProcess.spawn("server", () -> Channel.recv(CommunicationModel.P2P));
            Channel.send(CommunicationModel.P2P, server.tid(), new Value(1));
            server.await();
        }

        @Override
        public String programName() {
            return "main-sends";
        }

        @Override
        public String size() {
            return "1";
        }

        @Override
        public CommunicationModel model() {
            return CommunicationModel.P2P;
        }

        @Override
        public List<String> rolesInSpawnOrder() {
            return List.of("server");
        }
    }

    /** A client sends a floating-point value, which the summary format refuses. */
    public static final class BadValue implements MpProgram {
        /**
         * A message with a floating-point field.
         *
         * @param d the value
         */
        @Tag("D")
        public record D(double d) implements MpMessage {}

        /** Creates the program. */
        public BadValue() {}

        @Override
        public void run() {
            MpProcess.Handle server = MpProcess.spawn("server", () -> Channel.recv(CommunicationModel.P2P));
            MpProcess.Handle client = MpProcess.spawn("client",
                    () -> Channel.send(CommunicationModel.P2P, server.tid(), new D(0.5)));
            server.await();
            client.await();
        }

        @Override
        public String programName() {
            return "bad-value";
        }

        @Override
        public String size() {
            return "1";
        }

        @Override
        public CommunicationModel model() {
            return CommunicationModel.P2P;
        }

        @Override
        public List<String> rolesInSpawnOrder() {
            return List.of("server", "client");
        }
    }

    /** A server waits for a message nobody sends: every execution is blocked. */
    public static final class BlockedOnly implements Runnable {
        /** Creates the program. */
        public BlockedOnly() {}

        @Override
        public void run() {
            MpProcess.Handle server = MpProcess.spawn("server", () -> Channel.recv(CommunicationModel.P2P));
            server.await();
        }
    }

    /**
     * {@link NamedCliTest.Choice} with a third role that makes {@code k} choices among ten values and sends
     * nothing: its summaries are {@code Choice}'s two whatever {@code k}, its executions {@code 2 * 10^k}.
     */
    public static final class Wide implements MpProgram {
        private final int k;

        /**
         * Creates the program.
         *
         * @param args the number of choices of the third role
         */
        public Wide(String... args) {
            this.k = Integer.parseInt(args[0]);
        }

        @Override
        public void run() {
            int choices = k;
            MpProcess.Handle server = MpProcess.spawn("server", () -> Channel.recv(CommunicationModel.P2P));
            MpProcess.Handle client = MpProcess.spawn("client", () -> Channel.send(CommunicationModel.P2P,
                    server.tid(), new NamedCliTest.Choice.Ping(Nondet.choose(List.of(1, 2)))));
            MpProcess.Handle noise = MpProcess.spawn("noise", () -> {
                for (int i = 0; i < choices; i++) {
                    Nondet.choose(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
                }
            });
            server.await();
            client.await();
            noise.await();
        }

        @Override
        public String programName() {
            return "wide";
        }

        @Override
        public String size() {
            return "1";
        }

        @Override
        public CommunicationModel model() {
            return CommunicationModel.P2P;
        }

        @Override
        public List<String> rolesInSpawnOrder() {
            return List.of("server", "client", "noise");
        }
    }

    /** A plain program that sends under causal delivery, which must does not support. */
    public static final class Causal implements Runnable {
        /** Creates the program. */
        public Causal() {}

        @Override
        public void run() {
            MpProcess.Handle server = MpProcess.spawn("server", () -> Channel.recv(CommunicationModel.CD));
            MpProcess.Handle client = MpProcess.spawn("client",
                    () -> Channel.send(CommunicationModel.CD, server.tid(), new Value(1)));
            server.await();
            client.await();
        }
    }

    private static NamedCliTest.Out launch(Path d, String... args) throws Exception {
        return NamedCliTest.launchIn(d, args);
    }

    private static void expect(int status, NamedCliTest.Out o, String what) {
        assertEquals(status, o.exit(), what + "\n" + o.text());
    }

    @Test
    void exit0_conformsAndExhausted() throws Exception {
        Path d = NamedCliTest.freshDir("exit0");
        expect(0, launch(d, "count", "--program", CHOICE, "--seed", "1"), "count, exhausted");
        expect(0, launch(d, "export", "--program", CHOICE, "--seed", "1", "--out", "spec.jsonl"), "export");
        expect(0, launch(d, "online", "--program", CHOICE, "--seed", "1", "--spec", "spec.jsonl"), "conforms");
    }

    @Test
    void exit1_uncoveredSummary_withAndWithoutStopAtFirst() throws Exception {
        Path d = NamedCliTest.freshDir("exit1-online");
        expect(0, launch(d, "export", "--program", CHOICE, "--seed", "1", "--out", "all.jsonl"), "export");
        List<String> all = Files.readAllLines(d.resolve("all.jsonl"), StandardCharsets.UTF_8);
        Files.write(d.resolve("one.jsonl"), all.subList(0, 2), StandardCharsets.UTF_8);
        NamedCliTest.Out full = launch(d, "online", "--program", CHOICE, "--seed", "1", "--spec", "one.jsonl");
        expect(1, full, "reports");
        assertTrue(full.stdout().contains("verdict:    reports"), full.stdout());
        NamedCliTest.Out stop = launch(d, "online", "--program", CHOICE, "--seed", "1", "--spec", "one.jsonl",
                "--stop-at-first");
        expect(1, stop, "reports, stopped at the first miss");
        // The positional form keeps exit 0 for a completed run, whatever its verdict.
        expect(0, launch(d, CHOICE, "must", "0", "1", "rep-pos", "-", "600", "--spec", "one.jsonl"),
                "positional reports");
    }

    @Test
    void exit1_failedAssertionInASpawnedProcess_onTheLastIteration() throws Exception {
        Path d = NamedCliTest.freshDir("exit1-process");
        NamedCliTest.Out last = launch(d, "count", "--program", FAILS + ":process", "--seed", "1", "--iterations",
                "2");
        expect(1, last, "the failing execution is the second and last iteration");
        assertTrue(last.stdout().contains("FAILED:") && last.stdout().contains("the client chose 2"), last.stdout());
        // With one iteration the failing execution is not reached: the run is incomplete, not a violation.
        expect(3, launch(d, "count", "--program", FAILS + ":process", "--seed", "1", "--iterations", "1"),
                "one iteration, bounded");
        expect(1, launch(d, "count", "--program", FAILS + ":process", "--seed", "1"), "unbounded");
        expect(1, launch(d, FAILS + ":process", "must", "2", "1", "rep-pos"), "positional");
    }

    @Test
    void exit1_failedAssertionInMain_onTheLastIteration() throws Exception {
        Path d = NamedCliTest.freshDir("exit1-main");
        NamedCliTest.Out last = launch(d, "count", "--program", FAILS + ":main", "--seed", "1", "--iterations", "2");
        expect(1, last, "the failing execution is the second and last iteration");
        assertTrue(last.stdout().contains("main received 2"), last.stdout());
        expect(3, launch(d, "count", "--program", FAILS + ":main", "--seed", "1", "--iterations", "1"),
                "one iteration, bounded");
        // mp-random with one iteration: the failing iteration is the only, hence the last, one.
        expect(1, launch(d, "count", "--program", FAILS + ":always", "--strategy", "mp-random", "--seed", "1",
                "--iterations", "1"), "mp-random, last iteration");
        expect(1, launch(d, FAILS + ":main", "must", "2", "1", "rep-pos"), "positional");
    }

    @Test
    void exit2_refusedRulesModelsAndOutputs() throws Exception {
        Path d = NamedCliTest.freshDir("exit2");
        NamedCliTest.Out shape = launch(d, "export", "--program", MainSends.class.getName(), "--seed", "1", "--out",
                "o.jsonl");
        expect(2, shape, "an export shape rule");
        assertTrue(shape.stdout().contains("the format requires main"), shape.stdout());
        // Positional: the same refusal is a failed run.
        expect(1, launch(d, MainSends.class.getName(), "must", "0", "1", "rep-pos", "o2.jsonl"), "positional shape");
        NamedCliTest.Out causal = launch(d, "count", "--program", Causal.class.getName(), "--seed", "1");
        expect(2, causal, "a model must does not support, met at run time");
        assertTrue(causal.stdout().contains("which must does not support"), causal.stdout());
        expect(2, launch(d, "count", "--program", CHOICE, "--strategy", "pct"), "not a message-passing strategy");
        Files.writeString(d.resolve("plain"), "x");
        expect(2, launch(d, "export", "--program", CHOICE, "--seed", "1", "--out", "plain/o.jsonl"),
                "an output that cannot be written");
    }

    @Test
    void exit3_partialBoundedTimedOutAndSampled() throws Exception {
        Path d = NamedCliTest.freshDir("exit3");
        expect(0, launch(d, "export", "--program", CHOICE, "--seed", "1", "--out", "spec.jsonl"), "export");
        NamedCliTest.Out partial = launch(d, "online", "--program", CHOICE, "--seed", "1", "--spec", "spec.jsonl",
                "--iterations", "1");
        expect(3, partial, "online, bounded before the end");
        assertTrue(partial.stdout().contains("verdict:    partial"), partial.stdout());
        expect(3, launch(d, "count", "--program", CHOICE, "--seed", "1", "--iterations", "1"), "count, bounded");
        NamedCliTest.Out timeout = launch(d, "count", "--program", Many.class.getName(), "--seed", "1", "--timeout",
                "1");
        expect(3, timeout, "count, timed out");
        assertTrue(timeout.stdout().contains("due to timeout"), timeout.stdout());
        expect(3, launch(d, "count", "--program", CHOICE, "--strategy", "mp-random", "--seed", "1", "--iterations",
                "20"), "mp-random samples");
        expect(3, launch(d, "export", "--program", CHOICE, "--strategy", "mp-random", "--seed", "1", "--iterations",
                "20", "--out", "sampled.jsonl"), "mp-random export, sampled");
    }

    @Test
    void exit1_programFailuresOfEveryKind_sequentialAndSplit() throws Exception {
        Path d = NamedCliTest.freshDir("exit1-kinds");
        for (String where : List.of("main", "process", "boom", "nomsg", "recording", "guard")) {
            NamedCliTest.Out seq = launch(d, "count", "--program", FAILS + ":" + where, "--seed", "1",
                    "--report-dir", "rep-" + where);
            expect(1, seq, where + ", sequential");
            NamedCliTest.Out split = launch(d, "count", "--program", FAILS + ":" + where, "--seed", "1",
                    "--report-dir", "rep-split-" + where, "--split", "2", "--k", "1");
            expect(1, split, where + ", split");
            assertTrue(split.stdout().contains("FAILED:     incomplete: "), split.stdout());
        }
        expect(1, launch(d, "count", "--program", FAILS + ":processalways", "--strategy", "mp-random", "--seed",
                "1", "--iterations", "1"), "mp-random, a spawned process");
    }

    @Test
    void exit1_timeoutAfterAnOnlineMiss() throws Exception {
        Path d = NamedCliTest.freshDir("exit1-timeout");
        String wide = Wide.class.getName();
        expect(0, launch(d, "export", "--program", wide + ":0", "--seed", "1", "--out", "all.jsonl"), "export");
        List<String> all = Files.readAllLines(d.resolve("all.jsonl"), StandardCharsets.UTF_8);
        List<String> spec = new ArrayList<>(List.of(all.get(0)));
        all.subList(1, all.size()).stream().filter(l -> !l.contains("\"f\":[1]")).forEach(spec::add);
        assertEquals(2, spec.size(), "the specification keeps the summary of the choice 2 only: " + all);
        Files.write(d.resolve("spec.jsonl"), spec, StandardCharsets.UTF_8);
        NamedCliTest.Out o = launch(d, "online", "--program", wide + ":7", "--seed", "1", "--spec", "spec.jsonl",
                "--timeout", "2");
        expect(1, o, "an uncovered summary, then the timeout");
        assertTrue(o.stdout().contains("verdict:    reports") && o.stdout().contains("due to timeout"), o.stdout());
    }

    @Test
    void exit0_exhaustedBlockedOnly_and_exit2_valueRefusedUnderBothStrategies() throws Exception {
        Path d = NamedCliTest.freshDir("exit-blocked-value");
        NamedCliTest.Out blocked = launch(d, "count", "--program", BlockedOnly.class.getName(), "--seed", "1");
        expect(0, blocked, "every execution blocked, the exploration exhausted");
        assertTrue(blocked.stdout().contains("0 distinct complete, 1 distinct blocked; stack exhausted"),
                blocked.stdout());
        for (String strategy : List.of("must", "mp-random")) {
            NamedCliTest.Out o = launch(d, "export", "--program", BadValue.class.getName(), "--strategy", strategy,
                    "--seed", "1", "--iterations", "1", "--out", strategy + ".jsonl",
                    "--report-dir", "rep-" + strategy);
            expect(2, o, "a value the format refuses, under " + strategy);
            assertTrue(o.stdout().contains("floating-point values are refused"), o.stdout());
        }
    }
}
