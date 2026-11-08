package org.mpi_sws.jmc.mp.witness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.BANK;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.TIMEOUT;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.atIteration;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.field;
import static org.mpi_sws.jmc.mp.witness.WitnessSupport.launch;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.j8s.OnlineBank;
import org.mpi_sws.jmc.mp.summary.SpecificationFile;
import org.mpi_sws.jmc.mp.witness.WitnessSupport.Out;
import org.mpi_sws.jmc.mp.witness.WitnessSupport.Witness;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * WITNESS tester tests of the launcher's {@code --witness}: file mode (written once), directory mode
 * (one file per distinct uncovered summary, first-occurrence order), bug-finder mode, the refusals and
 * their exit codes, the export-file conflicts (symbolic links and {@code ..} included), exclusive creation
 * (CREATE_NEW, deterministically at the sink: a file that appears after open() is never overwritten), and
 * an execution that cannot be written (WitnessSink.refused). Refusals compare a snapshot of the run
 * directory (kinds, file contents, link targets) before and after.
 *
 * <p>Round 04: the two-concurrent-runs test was dropped. Both runs wrote identical witnesses, so it could not
 * tell which run wrote a file, and a run that opened after the other had written was refused (exit 2) without
 * reaching the write race; the deterministic collision test {@link
 * #sink_fileAppearingAfterOpen_neverOverwritten_errorRecorded} covers exclusive creation.
 *
 * <p>Program: OnlineBank n 3 (16 executions, 16 distinct summaries; the specification keeps body line 2
 * of its export, so 15 are uncovered, one execution each; visible role srv).
 */
class WitnessLauncherTest {

    /** OnlineBank:3, its full export and the specification (header + body line 2) in {@code dir}. */
    static Path bankSpec(Path dir) throws Exception {
        return WitnessSupport.spec(dir, BANK + ":3", "spec.jsonl", UnaryOperator.identity(), 2);
    }

    static Out online(Path dir, Path spec, String... extra) throws Exception {
        List<String> a = new ArrayList<>(List.of(BANK + ":3", "must", "0", "1", "rep", "-", TIMEOUT, "--spec",
                spec.toString()));
        a.addAll(List.of(extra));
        return launch(dir, a.toArray(new String[0]));
    }

    static List<String> bodyLines(Path file) throws Exception {
        List<String> l = Files.readAllLines(file, StandardCharsets.UTF_8);
        return l.subList(1, l.size());
    }

    static Set<String> listing(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return new HashSet<>(s.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void fileMode_fullModeWithManyUncovered_writtenOnce() throws Exception {
        Path dir = WitnessSupport.fresh("file-once");
        Path spec = bankSpec(dir);
        Path file = dir.resolve("w.txt");
        Out o = online(dir, spec, "--witness", file.toString());
        assertEquals(0, o.exit(), o.text());
        assertEquals("15 graphs (15 distinct summaries)", field(o.stdout(), "uncovered"));
        assertEquals("1 written to " + file, field(o.stdout(), "witnesses"));
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Witness w = WitnessSupport.checkConsistent(text, new OnlineBank("3").visibleRoles());
        assertEquals(atIteration(o.stdout()), w.iteration());
        assertEquals(field(o.stdout(), "witness"), w.summaryLine());
        // The same run in directory mode: its first file is the file mode's text.
        Path wd = dir.resolve("wd");
        Out d = online(dir, spec, "--witness", wd + "/");
        assertEquals(0, d.exit(), d.text());
        assertEquals(text, Files.readString(wd.resolve("witness-1.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void directoryMode_oneFilePerDistinctSummary_firstOccurrenceOrder() throws Exception {
        Path dir = WitnessSupport.fresh("dir-order");
        Path spec = bankSpec(dir);
        Path wd = dir.resolve("wd");
        // --witness before the program: accepted anywhere in the argument list.
        List<String> a = new ArrayList<>(List.of("--witness", wd + "/", BANK + ":3", "must", "0", "1", "rep", "-",
                TIMEOUT, "--spec", spec.toString()));
        Out o = launch(dir, a.toArray(new String[0]));
        assertEquals(0, o.exit(), o.text());
        assertEquals("15 graphs (15 distinct summaries)", field(o.stdout(), "uncovered"));
        assertEquals("15 written to " + wd + "/", field(o.stdout(), "witnesses"));
        List<String> texts = WitnessSupport.witnessFiles(wd, 15);
        // Derived: the uncovered lines are the export's lines (n 1 each) except the specification's.
        Set<String> expected = new HashSet<>(bodyLines(dir.resolve("full-spec.jsonl")));
        assertTrue(expected.remove(bodyLines(spec).get(0)));
        Set<String> got = new HashSet<>();
        int last = 0;
        for (int n = 0; n < texts.size(); n++) {
            Witness w = WitnessSupport.checkConsistent(texts.get(n), new OnlineBank("3").visibleRoles());
            assertTrue(got.add(w.summaryLine()), "distinct: " + w.summaryLine());
            assertTrue(w.iteration() > last, "witness-" + (n + 1) + " at iteration " + w.iteration() + " after " + last);
            last = w.iteration();
            if (n == 0) {
                assertEquals(atIteration(o.stdout()), w.iteration());
                assertEquals(field(o.stdout(), "witness"), w.summaryLine());
            }
        }
        assertEquals(expected, got);
        assertTrue(last <= 16);
    }

    @Test
    void directoryMode_summaryUncoveredThreeTimes_oneFileAtItsFirstOccurrence() throws Exception {
        // WitnessRepeat: three executions (the invisible n's nondet), one summary; the spec's line has A(2)
        // for A(1), so the summary is uncovered three times (derived: 3 graphs, 1 distinct), one witness.
        Path dir = WitnessSupport.fresh("dir-repeat");
        String p = WitnessSupport.PKG + "WitnessRepeat";
        Path spec = WitnessSupport.spec(dir, p, "spec.jsonl", l -> l.replace("\"f\":[1]", "\"f\":[2]"), 1);
        Path wd = dir.resolve("wd");
        Out o = launch(dir, p, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", spec.toString(), "--witness", wd + "/");
        assertEquals(0, o.exit(), o.text());
        assertEquals("3 graphs (1 distinct summaries)", field(o.stdout(), "uncovered"));
        assertEquals("1 written to " + wd + "/", field(o.stdout(), "witnesses"));
        Witness w = WitnessSupport.checkConsistent(WitnessSupport.witnessFiles(wd, 1).get(0), List.of("srv"));
        assertEquals(atIteration(o.stdout()), w.iteration(), "the first occurrence's execution");
        assertEquals("NONDET 0", w.threads().get(3).events().get(1).body(), "n's first choice at the first occurrence");
    }

    @Test
    void directoryMode_existingDirWithoutSlash_andMissingParentsCreated() throws Exception {
        Path dir = WitnessSupport.fresh("dir-forms");
        Path spec = bankSpec(dir);
        Path existing = Files.createDirectories(dir.resolve("existing"));
        Files.writeString(existing.resolve("notes.txt"), "kept");
        Out o = online(dir, spec, "--witness", existing.toString());
        assertEquals(0, o.exit(), o.text());
        assertEquals("15 written to " + existing, field(o.stdout(), "witnesses"));
        assertEquals(16, listing(existing).size(), "15 witnesses beside notes.txt");
        Path deep = dir.resolve("a/b/c");
        Out d = online(dir, spec, "--witness", deep + "/");
        assertEquals(0, d.exit(), d.text());
        WitnessSupport.witnessFiles(deep, 15);
        // A relative directory resolves against the working directory.
        Out r = online(dir, spec, "--witness", "rel/");
        assertEquals(0, r.exit(), r.text());
        assertEquals("15 written to rel/", field(r.stdout(), "witnesses"));
        WitnessSupport.witnessFiles(dir.resolve("rel"), 15);
    }

    @Test
    void bugFinderMode_directory_writesOne() throws Exception {
        Path dir = WitnessSupport.fresh("bug-finder");
        Path spec = bankSpec(dir);
        Path wd = dir.resolve("wd");
        Out o = online(dir, spec, "--stop-at-first", "--witness", wd + "/");
        assertEquals(0, o.exit(), o.text());
        assertEquals("1 graphs (1 distinct summaries)", field(o.stdout(), "uncovered"));
        assertEquals("1 written to " + wd + "/", field(o.stdout(), "witnesses"));
        Witness w = WitnessSupport.checkConsistent(WitnessSupport.witnessFiles(wd, 1).get(0),
                new OnlineBank("3").visibleRoles());
        assertEquals(field(o.stdout(), "witness"), w.summaryLine());
        assertEquals(atIteration(o.stdout()), w.iteration());
    }

    @Test
    void conforming_writesNothing_noFileCreated() throws Exception {
        Path dir = WitnessSupport.fresh("conforms");
        Path spec = WitnessSupport.spec(dir, BANK + ":3", "spec.jsonl", UnaryOperator.identity(),
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
        Path file = dir.resolve("w.txt");
        Out o = online(dir, spec, "--witness", file.toString());
        assertEquals(0, o.exit(), o.text());
        assertEquals("conforms", field(o.stdout(), "verdict"));
        assertEquals("0 written to " + file, field(o.stdout(), "witnesses"));
        assertFalse(Files.exists(file));
        Path wd = dir.resolve("wd");
        Out d = online(dir, spec, "--witness", wd + "/");
        assertEquals(0, d.exit(), d.text());
        assertEquals(Set.of(), listing(wd), "the directory is created, empty");
    }

    /** A refused invocation: exit 2, the message, the usage line naming the option, nothing written. */
    static void refused(Path dir, String message, String... args) throws Exception {
        java.util.Map<String, String> before = tree(dir);
        Out o = launch(dir, args);
        assertEquals(2, o.exit(), o.text());
        assertTrue(o.stderr().contains(message), "message <" + message + ">: " + o.text());
        assertTrue(o.stderr().contains("[--witness <file or dir/>]"), "usage names --witness: " + o.stderr());
        assertFalse(o.stdout().contains("witnesses:"), o.stdout());
        assertEquals(before, tree(dir), "nothing created or changed by " + String.join(" ", args));
    }

    @Test
    void refusals_exit2_nothingWritten() throws Exception {
        Path dir = WitnessSupport.fresh("refusals");
        Path spec = bankSpec(dir);
        String p = BANK + ":3";
        String s = spec.toString();
        refused(dir, "--witness needs --spec", p, "must", "0", "1", "rep", "-", TIMEOUT, "--witness", "w.txt");
        refused(dir, "--witness cannot be combined with --split", p, "must", "0", "1", "rep", "-", TIMEOUT,
                "--spec", s, "--split", "2", "--witness", "w.txt");
        refused(dir, "--witness cannot be combined with --split", p, "must", "0", "1", "rep", "-", TIMEOUT,
                "--spec", s, "--k", "2", "--witness", "wd/");
        refused(dir, "--witness cannot be combined with --split", p, "must", "0", "1", "rep", "-", TIMEOUT,
                "--spec", s, "--worker", dir.resolve("run").toString(), "0", "--witness", "w.txt");
        refused(dir, "--witness given twice", p, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", s,
                "--witness", "a.txt", "--witness", "b.txt");
        refused(dir, "--witness needs a file or a directory", p, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", s,
                "--witness");
        refused(dir, "its directory does not exist", p, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", s,
                "--witness", "missing/w.txt");
        Files.writeString(dir.resolve("old.txt"), "an earlier witness");
        refused(dir, "the file exists", p, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", s, "--witness", "old.txt");
        assertEquals("an earlier witness", Files.readString(dir.resolve("old.txt")), "not overwritten");
        Path held = Files.createDirectories(dir.resolve("held"));
        Files.writeString(held.resolve("witness-7.txt"), "x");
        refused(dir, "the directory already holds witness files", p, "must", "0", "1", "rep", "-", TIMEOUT,
                "--spec", s, "--witness", "held/");
        refused(dir, "the directory already holds witness files", p, "must", "0", "1", "rep", "-", TIMEOUT,
                "--spec", s, "--witness", "held");
        // The export file is the witness file, or lies directly in the witness directory.
        refused(dir, "it is the export file", p, "must", "0", "1", "rep", "out.jsonl", TIMEOUT, "--spec", s,
                "--witness", "out.jsonl");
        refused(dir, "it is the export file", p, "must", "0", "1", "rep", "out.jsonl", TIMEOUT, "--spec", s,
                "--witness", dir.resolve("out.jsonl").toString());
        refused(dir, "lies in the witness directory", p, "must", "0", "1", "rep", "wd/out.jsonl", TIMEOUT, "--spec",
                s, "--witness", "wd/");
        refused(dir, "lies in the witness directory", p, "must", "0", "1", "rep", "wd/any-name.txt", TIMEOUT,
                "--spec", s, "--witness", dir.resolve("wd").toString() + "/");
        refused(dir, "lies in the witness directory", p, "must", "0", "1", "rep", "./out.jsonl", TIMEOUT, "--spec",
                s, "--witness", "./");
        assertFalse(Files.exists(dir.resolve("wd")), "a refused witness directory is not created");
    }

    @Test
    void exportBesideWitness_dashAndOtherDirectories_accepted() throws Exception {
        Path dir = WitnessSupport.fresh("export-beside");
        Path spec = bankSpec(dir);
        String p = BANK + ":3";
        Out a = launch(dir, p, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", spec.toString(), "--witness", "wd/");
        assertEquals(0, a.exit(), a.text());
        assertEquals("15 written to wd/", field(a.stdout(), "witnesses"));
        // Export beside the witness file, and in a subdirectory of the witness directory (not directly in it).
        Out b = launch(dir, p, "must", "0", "1", "rep", "out.jsonl", TIMEOUT, "--spec", spec.toString(),
                "--witness", "w.txt");
        assertEquals(0, b.exit(), b.text());
        assertEquals(17, Files.readAllLines(dir.resolve("out.jsonl")).size());
        assertTrue(Files.exists(dir.resolve("w.txt")));
        Out c = launch(dir, p, "must", "0", "1", "rep", "wd2/sub/out.jsonl", TIMEOUT, "--spec", spec.toString(),
                "--witness", "wd2/");
        assertEquals(0, c.exit(), c.text());
        assertEquals(Set.of("out.jsonl"), listing(dir.resolve("wd2/sub")));
        assertEquals(Set.of("sub", "witness-1.txt", "witness-2.txt", "witness-3.txt", "witness-4.txt",
                "witness-5.txt", "witness-6.txt", "witness-7.txt", "witness-8.txt", "witness-9.txt", "witness-10.txt",
                "witness-11.txt", "witness-12.txt", "witness-13.txt", "witness-14.txt", "witness-15.txt"),
                listing(dir.resolve("wd2")));
    }

    // ---------------------------------------------------------------- the sink (MpLauncher.WitnessFiles)

    static Object openSink(String path) throws Exception {
        Class<?> c = Class.forName("org.mpi_sws.jmc.mp.launch.MpLauncher$WitnessFiles");
        Method open = c.getDeclaredMethod("open", String.class, Path.class);
        open.setAccessible(true);
        return open.invoke(null, path, null);
    }

    static Object call(Object sink, String name) throws Exception {
        Method m = sink.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(sink);
    }

    @Test
    void sink_fileAppearingAfterOpen_neverOverwritten_errorRecorded() throws Exception {
        Path dir = WitnessSupport.fresh("create-new");
        // Directory mode: witness-2.txt appears after open and after witness-1 was written.
        Path wd = dir.resolve("wd");
        MustStrategy.WitnessSink s = (MustStrategy.WitnessSink) openSink(wd + "/");
        s.uncovered(1, "L1", "one\n");
        Files.writeString(wd.resolve("witness-2.txt"), "foreign");
        s.uncovered(2, "L2", "two\n");
        s.uncovered(3, "L3", "three\n");
        assertEquals("foreign", Files.readString(wd.resolve("witness-2.txt")), "not overwritten");
        assertEquals("one\n", Files.readString(wd.resolve("witness-1.txt")));
        assertFalse(Files.exists(wd.resolve("witness-3.txt")), "the writing ended at the failure");
        assertEquals(1, call(s, "written"));
        String err = (String) call(s, "error");
        assertNotNull(err);
        assertTrue(err.contains(wd.resolve("witness-2.txt").toString()) && err.contains("FileAlreadyExists"), err);
        // File mode: the file appears after open.
        Path f = dir.resolve("w.txt");
        MustStrategy.WitnessSink t = (MustStrategy.WitnessSink) openSink(f.toString());
        Files.writeString(f, "foreign");
        t.uncovered(1, "L1", "one\n");
        assertEquals("foreign", Files.readString(f));
        assertEquals(0, call(t, "written"));
        assertTrue(((String) call(t, "error")).contains(f.toString()));
        // File mode: written once, the second call ignored; a refusal after it is ignored too.
        Path g = dir.resolve("g.txt");
        MustStrategy.WitnessSink u = (MustStrategy.WitnessSink) openSink(g.toString());
        u.uncovered(4, "L4", "four\n");
        u.uncovered(5, "L5", "five\n");
        u.refused(6, "L6", "why");
        assertEquals("four\n", Files.readString(g));
        assertEquals(1, call(u, "written"));
        assertNull(call(u, "error"));
        // A write failure (an unwritable directory): recorded, nothing written.
        Path ro = Files.createDirectories(dir.resolve("ro"));
        MustStrategy.WitnessSink v = (MustStrategy.WitnessSink) openSink(ro + "/");
        Files.setPosixFilePermissions(ro, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            v.uncovered(1, "L1", "one\n");
            assertEquals(0, call(v, "written"));
            assertTrue(((String) call(v, "error")).startsWith("cannot write witness " + ro.resolve("witness-1.txt")));
        } finally {
            Files.setPosixFilePermissions(ro, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        // A refusal first: recorded, and the writing ends.
        Path wr = dir.resolve("wr");
        MustStrategy.WitnessSink x = (MustStrategy.WitnessSink) openSink(wr + "/");
        x.refused(3, "L3", "because");
        x.uncovered(4, "L4", "four\n");
        assertEquals("cannot write the witness of iteration 3: because", call(x, "error"));
        assertEquals(Set.of(), listing(wr));
    }

    // ---------------------------------------------------------------- an execution that cannot be written

    /** The online report lines of a run. */
    static List<String> onlineLines(String stdout) {
        List<String> l = new ArrayList<>();
        for (String k : List.of("ran", "graphs", "lookups", "verdict", "mode", "uncovered", "witness", "at")) {
            l.add(k + ": " + field(stdout, k));
        }
        l.add("first miss iteration: " + field(stdout, "first miss").replaceAll(", \\d+ ms$", ""));
        l.add("first_miss_iteration: " + field(stdout, "first_miss_iteration"));
        l.add("uncovered_total: " + field(stdout, "uncovered_total"));
        return l;
    }

    static void unwritable(String kind, String why) throws Exception {
        unwritable("unwritable-" + kind, WitnessSupport.PKG + "WitnessUnwritable:" + kind, why, false);
    }

    /**
     * {@code p} has two executions, srv reading A(0) or A(1), and the A(1) one cannot be written as a
     * witness. The specification keeps srv's A(0) line, so the A(1) execution is uncovered (derived);
     * with and without {@code --stop-at-first}.
     */
    static void unwritable(String name, String p, String why, boolean stopAtFirst) throws Exception {
        Path dir = WitnessSupport.fresh(name);
        Path spec = WitnessSupport.specWhere(dir, p, "spec.jsonl", l -> l.contains("{\"t\":\"A\",\"f\":[0]}"));
        List<String> mode = stopAtFirst ? List.of("--stop-at-first") : List.of();
        List<String> base = new ArrayList<>(List.of(p, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", spec.toString()));
        base.addAll(mode);
        Out plain = launch(dir, base.toArray(new String[0]));
        assertEquals(0, plain.exit(), plain.text());
        assertEquals("reports", field(plain.stdout(), "verdict"));
        assertTrue(field(plain.stdout(), "witness").contains("{\"t\":\"A\",\"f\":[1]}"), plain.stdout());
        assertEquals(stopAtFirst ? "bug-finder (--stop-at-first)" : "full", field(plain.stdout(), "mode"));
        for (String target : List.of("wd/", "w.txt")) {
            List<String> a = new ArrayList<>(base);
            a.addAll(List.of("--witness", target));
            Out o = launch(dir, a.toArray(new String[0]));
            assertEquals(1, o.exit(), o.text());
            assertEquals(onlineLines(plain.stdout()), onlineLines(o.stdout()), "the online lines are intact");
            assertEquals("0 written to " + target, field(o.stdout(), "witnesses"));
            // Searched in the whole stdout: the reason may quote a name holding a line separator.
            String failed = "FAILED:     cannot write the witness of iteration " + atIteration(plain.stdout()) + ": ";
            int at = o.stdout().indexOf(failed);
            assertTrue(at >= 0, o.stdout());
            assertTrue(o.stdout().indexOf(why, at) > at, o.stdout());
            String line = o.stdout().substring(at, o.stdout().indexOf('\n', at));
            assertTrue(line.endsWith(why) || !why.startsWith("the name or message"), "the reason ends the line: " + line);
            assertTrue(o.stdout().chars().noneMatch(c -> (c >= 0x7f && c <= 0x9f) || c == 0x2028 || c == 0x2029),
                    "no raw C1 control, DEL or line/paragraph separator on stdout");
            assertEquals("error", field(o.stdout(), "stop_reason"));
        }
        assertEquals(Set.of(), listing(dir.resolve("wd")));
        assertFalse(Files.exists(dir.resolve("w.txt")));
    }

    @Test
    void unwritable_untaggedRecordFromInvisibleProcess_runFails_onlineLinesIntact() throws Exception {
        unwritable("untagged", "has no @Tag");
    }

    @Test
    void unwritable_payloadNamingDaemonFromInvisibleProcess_runFails_onlineLinesIntact() throws Exception {
        unwritable("daemon", "payload names daemon m/0");
    }

    @Test
    void unwritable_bugFinderMode_runFails_onlineLinesIntact() throws Exception {
        unwritable("unwritable-stop", WitnessSupport.PKG + "WitnessUnwritable:untagged", "has no @Tag", true);
    }

    @Test
    void roleWithLineBreak_lineFeedAndLineSeparator_refused_runFails_onlineLinesIntact() throws Exception {
        // The sender's role holds a line break: it passes the declared-role check, the summary (srv's only)
        // encodes, the witness is refused.
        // The reason quotes the name with the character escaped as a backslash-u and four lower-case hex digits,
        // and ends the FAILED line.
        String tail = " holds a control character or line separator, which a witness line cannot hold";
        String bs = "\\";
        unwritable("odd-nl", WitnessSupport.PKG + "WitnessOddRole:nl", "the name or message \"c" + bs + "u000ad\"" + tail,
                false);
        unwritable("odd-ls", WitnessSupport.PKG + "WitnessOddRole:ls", "the name or message \"c" + bs + "u2028d\"" + tail,
                false);
        unwritable("odd-del", WitnessSupport.PKG + "WitnessOddRole:del", "the name or message \"c" + bs + "u007fd\""
                + tail, false);
        unwritable("odd-csi", WitnessSupport.PKG + "WitnessOddRole:csi", "the name or message \"c" + bs + "u009bd\""
                + tail, false);
        unwritable("odd-nel", WitnessSupport.PKG + "WitnessOddRole:nel", "the name or message \"c" + bs + "u0085d\""
                + tail, false);
        unwritable("odd-ps", WitnessSupport.PKG + "WitnessOddRole:ps", "the name or message \"c" + bs + "u2029d\""
                + tail, false);
        unwritable("odd-nlcsi", WitnessSupport.PKG + "WitnessOddRole:nlcsi", "the name or message \"c" + bs + "u000a"
                + bs + "u009bd\"" + tail, false);
    }

    // ---------------------------------------------------------------- symbolic links

    /** A refused invocation in {@code dir}: exit 2, the message, and no file created anywhere under dir. */
    static void refusedNothingCreated(Path dir, String message, String... args) throws Exception {
        java.util.Map<String, String> before = tree(dir);
        Out o = launch(dir, args);
        assertEquals(2, o.exit(), o.text());
        assertTrue(o.stderr().contains(message), "message <" + message + ">: " + o.text());
        assertEquals(before, tree(dir), "nothing created or changed by " + String.join(" ", args));
    }

    /**
     * A snapshot of everything under {@code dir} (links not followed), the report directories excluded: per
     * entry its kind and, for a file, its content, for a symbolic link, its target.
     */
    static java.util.Map<String, String> tree(Path dir) throws Exception {
        java.util.Map<String, String> snap = new java.util.TreeMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path x : s.toList()) {
                String rel = dir.relativize(x).toString();
                if (rel.startsWith("rep")) {
                    continue;
                }
                if (Files.isSymbolicLink(x)) {
                    snap.put(rel, "link -> " + Files.readSymbolicLink(x));
                } else if (Files.isDirectory(x, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    snap.put(rel, "dir");
                } else {
                    snap.put(rel, "file " + java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(x))));
                }
            }
        }
        return snap;
    }

    @Test
    void symlinkDirectoryAlias_exportAndWitnessOneFile_refused() throws Exception {
        Path dir = WitnessSupport.fresh("symlinks");
        Path spec = bankSpec(dir);
        String p = BANK + ":3";
        String s = spec.toString();
        Path real = Files.createDirectories(dir.resolve("real"));
        Files.createSymbolicLink(dir.resolve("alias"), real.getFileName());
        // A directory alias: alias -> real.
        refusedNothingCreated(dir, "it is the export file", p, "must", "0", "1", "rep", "alias/w.txt", TIMEOUT,
                "--spec", s, "--witness", "real/w.txt");
        refusedNothingCreated(dir, "it is the export file", p, "must", "0", "1", "rep", "real/w.txt", TIMEOUT,
                "--spec", s, "--witness", "alias/w.txt");
        refusedNothingCreated(dir, "lies in the witness directory", p, "must", "0", "1", "rep", "alias/witness-1.txt",
                TIMEOUT, "--spec", s, "--witness", "real/");
        refusedNothingCreated(dir, "lies in the witness directory", p, "must", "0", "1", "rep", "alias/any.jsonl",
                TIMEOUT, "--spec", s, "--witness", "real/");
        refusedNothingCreated(dir, "lies in the witness directory", p, "must", "0", "1", "rep", "real/any.jsonl",
                TIMEOUT, "--spec", s, "--witness", "alias");
        assertEquals(Set.of(), listing(real), "no witness and no export written");
    }

    @Test
    void symlinkExportFileItself_linkToWitnessPath_refused() throws Exception {
        Path dir = WitnessSupport.fresh("symlink-export");
        Path spec = bankSpec(dir);
        String p = BANK + ":3";
        String s = spec.toString();
        Path real = Files.createDirectories(dir.resolve("real"));
        // The export file itself a symbolic link to the witness path (dangling until the witness is written).
        Files.createSymbolicLink(dir.resolve("link.jsonl"), Path.of("real/w.txt"));
        refusedNothingCreated(dir, "it is the export file", p, "must", "0", "1", "rep", "link.jsonl", TIMEOUT,
                "--spec", s, "--witness", "real/w.txt");
        Files.delete(dir.resolve("link.jsonl"));
        Files.createSymbolicLink(dir.resolve("link.jsonl"), Path.of("real/witness-1.txt"));
        refusedNothingCreated(dir, "lies in the witness directory", p, "must", "0", "1", "rep", "link.jsonl", TIMEOUT,
                "--spec", s, "--witness", "real/");
        assertEquals(Set.of(), listing(real), "no witness and no export written");
    }

    @Test
    void symlinkExportChainsRelativeTargetsAndLoops_refused_unrelatedLinkWrittenThrough() throws Exception {
        Path dir = WitnessSupport.fresh("symlink-chains");
        Path spec = bankSpec(dir);
        String p = BANK + ":3";
        String s = spec.toString();
        Path real = Files.createDirectories(dir.resolve("real"));
        // A chain of two dangling links: e1.jsonl -> l2 -> real/w.txt.
        Files.createSymbolicLink(dir.resolve("l2"), Path.of("real/w.txt"));
        Files.createSymbolicLink(dir.resolve("e1.jsonl"), Path.of("l2"));
        refusedNothingCreated(dir, "it is the export file", p, "must", "0", "1", "rep", "e1.jsonl", TIMEOUT,
                "--spec", s, "--witness", "real/w.txt");
        // A dangling link with a relative target, resolved against the link's directory: sub/e2.jsonl -> ../real/w.txt.
        Path sub = Files.createDirectories(dir.resolve("sub"));
        Files.createSymbolicLink(sub.resolve("e2.jsonl"), Path.of("../real/w.txt"));
        refusedNothingCreated(dir, "it is the export file", p, "must", "0", "1", "rep", "sub/e2.jsonl", TIMEOUT,
                "--spec", s, "--witness", "real/w.txt");
        // Directory mode through a chain: e3.jsonl -> l3 -> real/witness-1.txt.
        Files.createSymbolicLink(dir.resolve("l3"), Path.of("real/witness-1.txt"));
        Files.createSymbolicLink(dir.resolve("e3.jsonl"), Path.of("l3"));
        refusedNothingCreated(dir, "lies in the witness directory", p, "must", "0", "1", "rep", "e3.jsonl", TIMEOUT,
                "--spec", s, "--witness", "real/");
        // A loop a -> b -> a as the export file: refused (too many links), no hang.
        Files.createSymbolicLink(dir.resolve("a"), Path.of("b"));
        Files.createSymbolicLink(dir.resolve("b"), Path.of("a"));
        long t0 = System.nanoTime();
        refusedNothingCreated(dir, "too many symbolic links", p, "must", "0", "1", "rep", "a", TIMEOUT, "--spec", s,
                "--witness", "real/w.txt");
        assertTrue((System.nanoTime() - t0) / 1_000_000_000L < 120, "the loop is refused promptly");
        assertEquals(Set.of(), listing(real));
        // A dangling export link to an unrelated path: accepted; the export is written through the link (as the
        // launcher of 648e9c83 writes it: Files.write follows the link).
        Path other = Files.createDirectories(dir.resolve("other"));
        Files.createSymbolicLink(dir.resolve("e4.jsonl"), Path.of("other/out.jsonl"));
        Out o = launch(dir, p, "must", "0", "1", "rep", "e4.jsonl", TIMEOUT, "--spec", s, "--witness", "real/w.txt");
        assertEquals(0, o.exit(), o.text());
        assertEquals("1 written to real/w.txt", field(o.stdout(), "witnesses"));
        assertTrue(Files.isSymbolicLink(dir.resolve("e4.jsonl")), "the link is kept");
        assertEquals(17, Files.readAllLines(other.resolve("out.jsonl")).size(), "header + 16 lines at the target");
        assertTrue(Files.readString(real.resolve("w.txt")).startsWith("uncovered summary (iteration "));
    }

    @Test
    void symlinkFollowedByDotDot_resolvedBeforeCollapsing_refused_plainDotDotAsBefore() throws Exception {
        // real/sub exists and alias -> real/sub, so alias/.. is real (the file system follows the link first),
        // not the run directory as a lexical normalisation says.
        Path dir = WitnessSupport.fresh("symlink-dotdot");
        Path spec = bankSpec(dir);
        String p = BANK + ":3";
        String s = spec.toString();
        Path real = Files.createDirectories(dir.resolve("real/sub"));
        Files.createSymbolicLink(dir.resolve("alias"), Path.of("real/sub"));
        refusedNothingCreated(dir, "it is the export file", p, "must", "0", "1", "rep", "alias/../w.txt", TIMEOUT,
                "--spec", s, "--witness", "real/w.txt");
        refusedNothingCreated(dir, "lies in the witness directory", p, "must", "0", "1", "rep", "alias/../witness-1.txt",
                TIMEOUT, "--spec", s, "--witness", "real/");
        refusedNothingCreated(dir, "lies in the witness directory", p, "must", "0", "1", "rep", "alias/../any.jsonl",
                TIMEOUT, "--spec", s, "--witness", "real");
        // The same inside a dangling link's target: e -> alias/../w.txt.
        Files.createSymbolicLink(dir.resolve("e.jsonl"), Path.of("alias/../w.txt"));
        refusedNothingCreated(dir, "it is the export file", p, "must", "0", "1", "rep", "e.jsonl", TIMEOUT, "--spec", s,
                "--witness", "real/w.txt");
        // And in the witness path: --witness alias/../w.txt against the export real/w.txt.
        refusedNothingCreated(dir, "it is the export file", p, "must", "0", "1", "rep", "real/w.txt", TIMEOUT,
                "--spec", s, "--witness", "alias/../w.txt");
        assertEquals(Set.of("sub"), listing(dir.resolve("real")), "real holds only sub");
        // Plain relative paths with ./ and ../ (no links): the same file is refused, another one is accepted.
        refusedNothingCreated(dir, "it is the export file", p, "must", "0", "1", "rep", "./real/sub/../w.txt", TIMEOUT,
                "--spec", s, "--witness", "real/w.txt");
        Out o = launch(dir, p, "must", "0", "1", "rep", "./real/sub/../out.jsonl", TIMEOUT, "--spec", s, "--witness",
                "real/sub/./w.txt");
        assertEquals(0, o.exit(), o.text());
        assertEquals("1 written to real/sub/./w.txt", field(o.stdout(), "witnesses"));
        assertEquals(17, Files.readAllLines(dir.resolve("real/out.jsonl")).size());
        assertTrue(Files.readString(dir.resolve("real/sub/w.txt")).startsWith("uncovered summary (iteration "));
    }

    // ---------------------------------------------------------------- in process

    @Test
    void inProcess_realSinkReceivesIterationLineAndText_equalToLauncherFiles() throws Exception {
        // WitnessTiny under must, seed 1, against header + [A(1), A(2)] (derived: 3 distinct uncovered, one
        // execution each), with a recording sink and no launcher; compared with the launcher's directory run.
        Path dir = WitnessSupport.fresh("in-process");
        String prog = WitnessSupport.PKG + "WitnessTiny";
        Path spec = WitnessSupport.spec(dir, prog, "spec.jsonl", UnaryOperator.identity(), 2);
        WitnessTiny program = new WitnessTiny();
        MustStrategy m = new MustStrategy(1L, dir.resolve("rep-in").toString(), program.rolesInSpawnOrder(),
                program.visibleRoles());
        m.checkAgainst(SpecificationFile.load(spec, program.visibleRoles()).index(), false);
        List<String[]> calls = new ArrayList<>();
        List<String> refusals = new ArrayList<>();
        m.writeWitnesses(new MustStrategy.WitnessSink() {
            @Override public void uncovered(int iteration, String summaryLine, String witness) {
                calls.add(new String[] {String.valueOf(iteration), summaryLine, witness});
            }

            @Override public void refused(int iteration, String summaryLine, String why) {
                refusals.add(iteration + " " + why);
            }
        });
        new org.mpi_sws.jmc.checker.JmcModelChecker(new org.mpi_sws.jmc.checker.JmcCheckerConfiguration.Builder()
                .strategyType("must").numIterations(0).seed(1L).reportPath(dir.resolve("rep-in").toString())
                .strategyConstructor(c -> m).timeout(java.time.Duration.ofSeconds(300)).build())
                .check(new org.mpi_sws.jmc.checker.JmcFunctionalTestTarget(WitnessTiny.class.getName(),
                        () -> org.mpi_sws.jmc.api.mp.MpProcess.runMain(program)));
        assertEquals(List.of(), refusals);
        assertEquals(3, calls.size());
        assertEquals(3, m.uncoveredDistinct());
        assertEquals(m.uncovered().orElseThrow().toJson(1), calls.get(0)[1], "the first call is the first miss");
        assertEquals(m.uncoveredIteration(), Integer.parseInt(calls.get(0)[0]));
        Path wd = dir.resolve("wd");
        Out o = launch(dir, prog, "must", "0", "1", "rep", "-", TIMEOUT, "--spec", spec.toString(), "--witness", wd + "/");
        assertEquals(0, o.exit(), o.text());
        List<String> files = WitnessSupport.witnessFiles(wd, 3);
        for (int n = 0; n < 3; n++) {
            String[] c = calls.get(n);
            Witness w = WitnessSupport.checkConsistent(c[2], program.visibleRoles());
            assertEquals(Integer.parseInt(c[0]), w.iteration(), "the iteration argument is line 1's");
            assertEquals(c[1], w.summaryLine(), "the line argument is line 1's");
            assertEquals(files.get(n), c[2], "the text is the launcher's witness-" + (n + 1) + ".txt");
        }
    }

    // ---------------------------------------------------------------- the strategy API

    @Test
    void strategyApi_writeWitnessesNeedsTheOnlineCheck() throws Exception {
        OnlineBank p = new OnlineBank("2");
        MustStrategy m = new MustStrategy(1L, "r", p.rolesInSpawnOrder(), p.visibleRoles());
        MustStrategy.WitnessSink sink = new MustStrategy.WitnessSink() {
            @Override public void uncovered(int iteration, String summaryLine, String witness) {}

            @Override public void refused(int iteration, String summaryLine, String why) {}
        };
        assertThrows(IllegalStateException.class, () -> m.writeWitnesses(sink));
        Path dir = WitnessSupport.fresh("api");
        Path spec = WitnessSupport.spec(dir, BANK + ":2", "spec.jsonl", UnaryOperator.identity(), 2);
        m.checkAgainst(SpecificationFile.load(spec, p.visibleRoles()).index(), false);
        assertThrows(NullPointerException.class, () -> m.writeWitnesses(null));
        m.writeWitnesses(sink);
    }
}
