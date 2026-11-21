package org.mpi_sws.jmc.mp.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The launcher's named form: the help, the subcommands against the positional form they translate to, the
 * {@code --model} option and its aliases, and the usage errors. Every case runs the launcher in a fresh JVM.
 */
class NamedCliTest {

    private static final String LAUNCHER = MpLauncher.class.getName();
    private static final String PROGRAM = Choice.class.getName();
    private static final Path ROOT = Path.of("build/test-results/named-cli").toAbsolutePath();

    /**
     * A client chooses 1 or 2 and sends it to a server: two executions, two summaries.
     */
    public static final class Choice implements MpProgram {

        /**
         * The one message.
         *
         * @param n the chosen value
         */
        @Tag("Ping")
        public record Ping(int n) implements MpMessage {}

        /** Creates the program. */
        public Choice() {}

        @Override
        public void run() {
            MpProcess.Handle server = MpProcess.spawn("server", () -> Channel.recv(CommunicationModel.P2P));
            MpProcess.Handle client = MpProcess.spawn("client", () -> Channel.send(CommunicationModel.P2P,
                    server.tid(), new Ping(Nondet.choose(List.of(1, 2)))));
            server.await();
            client.await();
        }

        @Override
        public String programName() {
            return "choice";
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

    /** A child run: its exit status, its stdout and its stderr. */
    record Out(int exit, String stdout, String stderr) {
        String text() {
            return stdout + stderr;
        }
    }

    private static Path fresh(String name) throws IOException {
        Path d = ROOT.resolve(name);
        if (Files.exists(d)) {
            try (Stream<Path> s = Files.walk(d)) {
                for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        Files.createDirectories(d);
        return d;
    }

    private static Out launch(Path cwd, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "-ea", "-cp", System.getProperty("java.class.path"), LAUNCHER));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(cwd.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null"))).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread a = pump(p.getInputStream(), out);
        Thread b = pump(p.getErrorStream(), err);
        if (!p.waitFor(300, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError("launcher did not finish in 300 s: " + String.join(" ", args));
        }
        a.join();
        b.join();
        return new Out(p.exitValue(), out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Thread pump(InputStream in, ByteArrayOutputStream to) {
        Thread t = new Thread(() -> {
            try (in) {
                in.transferTo(to);
            } catch (IOException e) {
                // the child is gone
            }
        });
        t.start();
        return t;
    }

    @Test
    void help_exitsZero_forTheLauncherAndEachCommand() throws Exception {
        Path d = fresh("help");
        Out all = launch(d, "--help");
        assertEquals(0, all.exit(), all.text());
        for (String c : NamedCli.COMMANDS) {
            assertTrue(all.stdout().contains("MpLauncher " + c + " "), all.stdout());
        }
        assertTrue(all.stdout().contains("deprecated"), all.stdout());
        for (String c : NamedCli.COMMANDS) {
            Out one = launch(d, c, "--help");
            assertEquals(0, one.exit(), one.text());
            assertTrue(one.stdout().contains("MpLauncher " + c + " "), one.stdout());
            Out viaHelp = launch(d, "help", c);
            assertEquals(one.stdout(), viaHelp.stdout());
        }
    }

    @Test
    void export_writesTheSameFileAsThePositionalForm() throws Exception {
        Path d = fresh("export");
        Out named = launch(d, "export", "--program", PROGRAM, "--seed", "1", "--report-dir", "rep-n", "--out",
                "named.jsonl", "--timeout", "60");
        assertEquals(0, named.exit(), named.text());
        Out positional = launch(d, PROGRAM, "must", "0", "1", "rep-p", "positional.jsonl", "60");
        assertEquals(0, positional.exit(), positional.text());
        byte[] n = Files.readAllBytes(d.resolve("named.jsonl"));
        assertArrayEquals(Files.readAllBytes(d.resolve("positional.jsonl")), n);
        assertEquals(3, Files.readAllLines(d.resolve("named.jsonl")).size(), "a header and two summaries");
        assertTrue(named.stdout().contains("\nmodel:      p2p\n") || named.stdout().startsWith("model:      p2p\n"),
                named.stdout());
        assertTrue(!positional.stdout().contains("model:  "), "the positional form's output is unchanged");
    }

    @Test
    void count_online_and_replay_run_underTheirDefaults() throws Exception {
        Path d = fresh("commands");
        Out count = launch(d, "count", "--program", PROGRAM);
        assertEquals(0, count.exit(), count.text());
        assertTrue(count.stdout().contains("graphs:     2 distinct complete, 0 distinct blocked; stack exhausted"),
                count.stdout());
        assertTrue(count.stdout().contains("iterations: unbounded, timeout 600 s"), count.stdout());
        assertTrue(count.stdout().contains("reportPath: build/mp-launch/NamedCliTest$Choice")
                || count.stdout().contains("reportPath: build/mp-launch/Choice"), count.stdout());

        Out export = launch(d, "export", "--program", PROGRAM, "--out", "spec.jsonl", "--graphs", "store.jsonl");
        assertEquals(0, export.exit(), export.text());
        Out online = launch(d, "online", "--program", PROGRAM, "--spec", "spec.jsonl");
        assertEquals(0, online.exit(), online.text());
        assertTrue(online.stdout().contains("verdict:    conforms"), online.stdout());

        List<String> store = Files.readAllLines(d.resolve("store.jsonl"), StandardCharsets.UTF_8);
        Files.writeString(d.resolve("g.json"), store.get(1) + "\n", StandardCharsets.UTF_8);
        Out replay = launch(d, "replay", "--graph", "g.json", "--allow-source-change");
        assertEquals(0, replay.exit(), replay.text());
        assertTrue(replay.stdout().contains("replay: reproduced ("), replay.stdout());
    }

    @Test
    void online_reportsAnUncoveredExecution_andWritesItsWitness() throws Exception {
        Path d = fresh("uncovered");
        Out export = launch(d, "export", "--program", PROGRAM, "--seed", "1", "--out", "all.jsonl");
        assertEquals(0, export.exit(), export.text());
        List<String> all = Files.readAllLines(d.resolve("all.jsonl"), StandardCharsets.UTF_8);
        Files.write(d.resolve("one.jsonl"), all.subList(0, 2), StandardCharsets.UTF_8);
        Out online = launch(d, "online", "--program", PROGRAM, "--seed", "1", "--spec", "one.jsonl", "--witness",
                "w/");
        assertTrue(online.stdout().contains("verdict:    reports"), online.stdout());
        assertTrue(online.stdout().contains("uncovered:  1 graphs (1 distinct summaries)"), online.stdout());
        assertTrue(online.stdout().contains("witnesses:  1 written to w/"), online.stdout());
        assertTrue(Files.exists(d.resolve("w/witness-1.txt")), "the witness file");
    }

    @Test
    void model_acceptsAliases_printsCanonically_andRefusesAMismatch() throws Exception {
        Path d = fresh("model");
        for (String alias : List.of("fifo", "p2p", "FIFO")) {
            Out o = launch(d, "count", "--program", PROGRAM, "--model", alias, "--report-dir", "rep-" + alias);
            assertEquals(0, o.exit(), o.text());
            assertTrue(o.stdout().startsWith("model:      p2p\n"), o.stdout());
        }
        Out bag = launch(d, "count", "--program", PROGRAM, "--model", "bag");
        assertEquals(2, bag.exit(), bag.text());
        assertTrue(bag.stderr().contains("--model asyn differs from the model p2p"), bag.stderr());
        for (String m : List.of("causal", "cd", "mbox")) {
            Out o = launch(d, "count", "--program", PROGRAM, "--model", m);
            assertEquals(2, o.exit(), o.text());
            assertTrue(o.stderr().contains("must supports the p2p and asyn models only"), o.stderr());
        }
        Out unknown = launch(d, "count", "--program", PROGRAM, "--model", "tcp");
        assertEquals(2, unknown.exit(), unknown.text());
        assertTrue(unknown.stderr().contains("unknown model tcp"), unknown.stderr());
    }

    @Test
    void usageErrors_exitTwo_withTheNamedUsageLine() throws Exception {
        Path d = fresh("usage");
        String[][] cases = {
            {"count"},
            {"count", "--program", PROGRAM, "--out", "x.jsonl"},
            {"count", "--program", PROGRAM, "--bogus"},
            {"count", "--program", PROGRAM, "--program", PROGRAM},
            {"count", "--program", PROGRAM, "stray"},
            {"count", "--program", PROGRAM, "--strategy", "trust"},
            {"count", "--program", PROGRAM, "--iterations"},
            {"export", "--program", PROGRAM},
            {"online", "--program", PROGRAM},
            {"replay"},
            {"replay", "--graph", "g.json", "--spec", "s.jsonl"},
            {"help", "count", "export"},
        };
        for (String[] c : cases) {
            Out o = launch(d, c);
            assertEquals(2, o.exit(), String.join(" ", c) + "\n" + o.text());
            assertTrue(o.stderr().contains("usage: MpLauncher "), o.stderr());
            assertTrue(!o.stderr().contains("[strategy=mp-random]"), "not the positional usage line: " + o.stderr());
        }
        Out trust = launch(d, "count", "--program", PROGRAM, "--strategy", "trust");
        assertTrue(trust.stderr().contains("not a message-passing strategy"), trust.stderr());
        Out iterations = launch(d, "count", "--program", PROGRAM, "--strategy", "mp-random", "--iterations", "0");
        assertEquals(2, iterations.exit(), iterations.text());
        assertTrue(iterations.stderr().contains("usage: MpLauncher count "), iterations.stderr());
    }

    private static NamedCli.Parsed parse(ByteArrayOutputStream err, String... args) {
        return NamedCli.parse(args, new java.io.PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new java.io.PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void parser_refusesAnOptionWhereAValueIsExpected() {
        String[][] cases = {
            {"online", "--program", PROGRAM, "--spec", "s.jsonl", "--out", "--stop-at-first"},
            {"replay", "--graph", "g.json", "--program", "--allow-source-change"},
            {"count", "--program", "--help"},
            {"count", "--program", PROGRAM, "--report-dir", "-h"},
        };
        for (String[] c : cases) {
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            NamedCli.Parsed p = parse(err, c);
            assertEquals(new NamedCli.Exit(2), p, String.join(" ", c));
            assertTrue(err.toString(StandardCharsets.UTF_8).contains(" needs a value before "), err.toString());
        }
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        NamedCli.Parsed negative = parse(err, "count", "--program", PROGRAM, "--seed", "-5");
        assertTrue(negative instanceof NamedCli.Run, "a negative number is a value: " + err);
    }

    @Test
    void parser_translatesDefaultsAndOptionsToThePositionalForm() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        NamedCli.Run r = (NamedCli.Run) parse(err, "count", "--program", PROGRAM, "--strategy", "mp-random",
                "--seed", "7", "--model", "bag");
        assertEquals(List.of(PROGRAM, "mp-random", "1000", "7", "build/mp-launch/Choice", "-", "600"),
                List.of(r.args()));
        assertEquals(CommunicationModel.ASYN, r.model());
        r = (NamedCli.Run) parse(err, "export", "--program", PROGRAM, "--seed", "1", "--out", "o.jsonl", "--graphs",
                "s.jsonl", "--graphs-limit", "3", "--split", "4", "--k", "9", "--timeout", "30", "--model", "causal",
                "--strategy", "mp-random", "--report-dir", "r");
        assertEquals(List.of(PROGRAM, "mp-random", "1000", "1", "r", "o.jsonl", "30", "--graphs", "s.jsonl",
                "--graphs-limit", "3", "--split", "4", "--k", "9"), List.of(r.args()));
        assertEquals(CommunicationModel.CD, r.model());
        r = (NamedCli.Run) parse(err, "online", "--program", PROGRAM, "--seed", "1", "--spec", "s.jsonl",
                "--stop-at-first", "--witness", "w/");
        assertEquals(List.of(PROGRAM, "must", "0", "1", "build/mp-launch/Choice", "-", "600", "--spec", "s.jsonl",
                "--witness", "w/", "--stop-at-first"), List.of(r.args()));
        r = (NamedCli.Run) parse(err, "replay", "--graph", "g.json", "--report", "r.json", "--allow-tool-change",
                "--program", PROGRAM);
        assertEquals(List.of("--replay", "g.json", "--report", "r.json", "--allow-tool-change", PROGRAM),
                List.of(r.args()));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }
}
