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
     * A client chooses 1 or 2 and sends it to main; with {@code process}, the client asserts it did not choose 2,
     * with {@code main} main asserts it did not receive 2, with {@code always} main fails in every execution.
     * {@code must} explores the choice 1 first, so the failing execution is the second, and with two iterations
     * the last one.
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
            });
            int got = Channel.recv(m, Value.class).n();
            client.await();
            assert !w.equals("main") || got != 2 : "main received 2";
            assert !w.equals("always") : "main always fails";
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
        expect(3, launch(d, "count", "--program", CHOICE, "--strategy", "mp-random", "--seed", "1", "--iterations",
                "20"), "mp-random samples");
        expect(3, launch(d, "export", "--program", CHOICE, "--strategy", "mp-random", "--seed", "1", "--iterations",
                "20", "--out", "sampled.jsonl"), "mp-random export, sampled");
    }
}
