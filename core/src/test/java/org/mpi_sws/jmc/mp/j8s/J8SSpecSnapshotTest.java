package org.mpi_sws.jmc.mp.j8s;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.summary.SpecificationFile;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * J8S tester round 04, Codex code-gate finding M1: the specification's digest and its index come from one
 * byte snapshot in each JVM ({@code SplitCoordinator.snapshotSpec}). OnlineBank:2's segments at K 1: root
 * {@code [1]}, item2 {@code [2,3]}, item1 {@code [4..6]}, item0 {@code [7..12]} (J8S round 01).
 */
class J8SSpecSnapshotTest {

    /** A private copy of a spec file (each case replaces its file's contents). */
    static Path copy(Path spec) throws Exception {
        Path p = J8SSupport.DIR.resolve("m1-" + UUID.randomUUID() + ".jsonl");
        Files.copy(spec, p);
        return p;
    }

    static String sha256(byte[] b) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
    }

    /** The {@code spec-*.snapshot} files in a run directory (none expected after any outcome). */
    static List<String> snapshots(Path d) throws Exception {
        List<String> out = new ArrayList<>();
        if (Files.isDirectory(d)) {
            try (Stream<Path> s = Files.walk(d)) {
                s.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("spec-") && n.endsWith(".snapshot"))
                        .forEach(out::add);
            }
        }
        return out;
    }

    static SplitCoordinator.Result run(Path d, SpecificationFile.Loaded loaded, int k, int w,
                                       BiFunction<Path, Integer, List<String>> cmd) throws Exception {
        OnlineBank p = new OnlineBank("2");
        return SplitCoordinator.run(new SplitCoordinator.Config(J8SSupport.BANK + "|p2p", p, p.rolesInSpawnOrder(),
                p.visibleRoles(), k, w, 300, d, cmd, false, Process::isAlive, loaded));
    }

    static SpecificationFile.Loaded load(Path spec) throws Exception {
        return SpecificationFile.load(spec, new OnlineBank("2").visibleRoles());
    }

    /**
     * M1 (a), Codex's counterexample, and (d) after a refused load. The caller loads bank2 without iteration
     * 3's line (11 lines); the file is then replaced by the complete export (12 lines); run at K 1, W 2.
     * Derived: the coordinator's snapshot has 12 lines, not 11, so {@code run} throws {@code IOException
     * ("specification … changed since it was loaded")}; no run.json, no worker, no snapshot file. (Before the
     * fix: the root ran against the 11-line index, the workers against the complete file, and the run
     * conformed.) Refused loads: the file is replaced by an empty file, by a header with enumeration
     * {@code partial}, and by a header with visible roles {@code [a]}: the load of the snapshot throws
     * {@code IllegalArgumentException} ("has no header line", "has enumeration \"partial\"", "has visible
     * roles [a]"), again with no snapshot file left; round 05: the message names the user's file
     * ({@code specification <file> …}) and no {@code .snapshot} path.
     */
    @Test
    void replacedAfterLoad_refused_notConforms_noSnapshotLeft() throws Exception {
        List<String> bad = new ArrayList<>();
        Path spec = copy(J8SSupport.bankSpec("m1a-drop3", 3));
        SpecificationFile.Loaded loaded = load(spec);
        Files.write(spec, J8SSupport.bankExport(), StandardCharsets.UTF_8);
        Path d = J8SApiTest.dir("m1a");
        String tag = UUID.randomUUID().toString();
        AtomicBoolean started = new AtomicBoolean();
        Throwable thrown = null;
        SplitCoordinator.Result r = null;
        try {
            r = run(d, loaded, 1, 2, (x, id) -> {
                started.set(true);
                return J8SApiTest.realWorker(x, id, J8SSupport.BANK, tag);
            });
        } catch (Throwable t) {
            thrown = t;
        }
        System.out.println("J8S M1a: loaded " + loaded.lines() + " lines; thrown " + thrown + "; result "
                + (r == null ? null : r.failure() + " online " + r.online()) + "; snapshots " + snapshots(d));
        if (!(thrown instanceof java.io.IOException) || !thrown.getMessage().contains("changed since it was loaded")) {
            bad.add("(a) expected IOException 'changed since it was loaded', got " + thrown + " / result "
                    + (r == null ? null : r.online()));
        }
        if (started.get() || Files.exists(d.resolve("run.json"))) {
            bad.add("(a) a worker was started or run.json written");
        }
        if (!snapshots(d).isEmpty()) {
            bad.add("(a) snapshot left: " + snapshots(d));
        }
        // refused loads (round 05: the message names the user's file, never the private .snapshot copy)
        String header = J8SSupport.bankExport().get(0);
        String[][] refusals = {
            // label, replacement text, expected fragment of the message
            {"empty file", "", "has no header line"},
            {"enumeration partial", header.replace("\"enumeration\":\"complete\"", "\"enumeration\":\"partial\"") + "\n",
                "has enumeration \"partial\""},
            {"visible roles differ", header.replace("\"visible\":[\"srv\"]", "\"visible\":[\"a\"]") + "\n",
                "has visible roles [a]"},
        };
        for (String[] c : refusals) {
            if (c[1].equals(header + "\n")) {
                bad.add("(d) " + c[0] + ": the replacement did not change the header");
                continue;
            }
            Path spec2 = copy(J8SSupport.bankSpec("m1a-drop3", 3));
            SpecificationFile.Loaded loaded2 = load(spec2);
            Files.writeString(spec2, c[1], StandardCharsets.UTF_8);
            Path d2 = J8SApiTest.dir("m1a-refused");
            Throwable thrown2 = null;
            try {
                run(d2, loaded2, 1, 2, (x, id) -> J8SApiTest.realWorker(x, id, J8SSupport.BANK, tag));
            } catch (Throwable t) {
                thrown2 = t;
            }
            String msg = thrown2 == null ? null : thrown2.getMessage();
            System.out.println("J8S M1a refused " + c[0] + ": thrown " + thrown2 + "; snapshots " + snapshots(d2));
            if (!(thrown2 instanceof IllegalArgumentException) || !msg.contains(c[2])) {
                bad.add("(d) " + c[0] + ": expected IllegalArgumentException '" + c[2] + "', got " + thrown2);
            } else if (!msg.contains("specification " + spec2 + " ") || msg.contains(".snapshot")) {
                bad.add("(d) " + c[0] + ": the message does not name " + spec2 + " or names a snapshot: " + msg);
            }
            if (Files.exists(d2.resolve("run.json")) || !snapshots(d2).isEmpty()) {
                bad.add("(d) " + c[0] + ": run.json or snapshot left " + snapshots(d2));
            }
        }
        assertEquals(List.of(), J8SApiTest.leftovers(tag));
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /**
     * M1 (b) and (d) after an abort. bank2-multi (3, 5, 9, 12), K 1, W 2. The file is replaced (by the
     * complete export) inside the worker command, which the coordinator calls after its snapshot and after
     * run.json (naming the original SHA-256) is written, before any worker JVM exists. Derived: every worker
     * that reaches its spec read hashes the new bytes and writes {@code worker-<id>.fail.json} ("… has
     * SHA-256 …, the run's is …"); the run aborts on the first such marker, never retried (no {@code *.1.json}
     * assignment), with no {@code item-*.json} committed, no worker left and no snapshot file left.
     */
    @Test
    void replacedBeforeWorkerRead_everyWorkerFailsAtStartup_noRetry_noCommit() throws Exception {
        Path spec = copy(J8SSupport.bankSpec("multi", 3, 5, 9, 12));
        byte[] original = Files.readAllBytes(spec);
        SpecificationFile.Loaded loaded = load(spec);
        Path d = J8SApiTest.dir("m1b");
        String tag = UUID.randomUUID().toString();
        AtomicBoolean replaced = new AtomicBoolean();
        String[] runJsonAtReplace = new String[1];
        SplitCoordinator.Result r = run(d, loaded, 1, 2, (x, id) -> {
            if (replaced.compareAndSet(false, true)) {
                try {
                    runJsonAtReplace[0] = Files.readString(x.resolve("run.json"));
                    Files.write(spec, J8SSupport.bankExport(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return J8SApiTest.realWorker(x, id, J8SSupport.BANK, tag);
        });
        List<String> bad = new ArrayList<>();
        JsonObject runJson = JsonParser.parseString(runJsonAtReplace[0]).getAsJsonObject();
        String named = runJson.getAsJsonObject("spec").get("sha256").getAsString();
        List<String> markers = new ArrayList<>();
        List<String> items = new ArrayList<>();
        List<String> retries = new ArrayList<>();
        try (Stream<Path> s = Files.list(d)) {
            s.forEach(p -> {
                String n = p.getFileName().toString();
                if (n.matches("worker-\\d+\\.fail\\.json")) {
                    try {
                        markers.add(n + ": " + JsonParser.parseString(Files.readString(p)).getAsJsonObject()
                                .get("reason").getAsString());
                    } catch (Exception e) {
                        markers.add(n + ": unreadable " + e);
                    }
                }
                if (n.matches("item-\\d+\\.json")) {
                    items.add(n);
                }
            });
        }
        try (Stream<Path> s = Files.list(d.resolve("assigned"))) {
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".1.json")).forEach(retries::add);
        }
        List<String> left = J8SApiTest.leftovers(tag);
        System.out.println("J8S M1b: run.json sha256 " + named + " (original " + sha256(original) + "); failure "
                + r.failure() + "; markers " + markers + "; items " + items + "; retries " + retries + "; leftovers "
                + left + "; snapshots " + snapshots(d));
        if (!named.equals(sha256(original))) {
            bad.add("run.json does not name the original bytes' SHA-256");
        }
        if (r.failure() == null || !r.failure().contains(".fail.json") || !r.failure().contains("SHA-256")) {
            bad.add("failure: " + r.failure());
        }
        if (markers.isEmpty()) {
            bad.add("no worker fail marker");
        }
        for (String m : markers) {
            if (!m.contains("has SHA-256 " + sha256(Files.readAllBytes(spec)) + ", the run's is " + named)) {
                bad.add("marker not the hash mismatch: " + m);
            }
        }
        if (!items.isEmpty()) {
            bad.add("committed results: " + items);
        }
        if (!retries.isEmpty()) {
            bad.add("retried: " + retries);
        }
        if (!left.isEmpty()) {
            bad.add("leftovers " + left);
        }
        if (!snapshots(d).isEmpty()) {
            bad.add("snapshot left: " + snapshots(d));
        }
        assertNotNull(r.online());
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /**
     * M1 (c) and (d) after success. Same line and duplicate counts, different content: the caller loads spec
     * A (bank2 without iteration 3's line), the file is replaced by spec B (11 lines, 0 duplicates, as A),
     * then run at K 1, W 2. Derived: the coordinator's snapshot passes the count check, and the whole run
     * checks against B; run.json names B's SHA-256 and the workers (which hash the file) start. Two B's:
     * <ul>
     *   <li>B = without iteration 1 (the root's only iteration): the root, run against the coordinator's
     *       index, misses: reports, first miss 1, witness iteration 1's line, uncovered 1 / 1. (Against A the
     *       root is covered and the workers, against B, cover iteration 3: conforms.)</li>
     *   <li>B = without iteration 9 (item0, first_local 3, offset 6): first miss 9, witness iteration 9's
     *       line, uncovered 1 / 1, the answer as in the sequential run against B; the coordinator re-queries
     *       that line against its own index (B), so it validates. (Against A, line 9 is covered: abort.)</li>
     * </ul>
     * In both, no failure, no snapshot file left, no worker left.
     */
    @Test
    void sameCountSubstitution_checksAgainstTheNewFileConsistently() throws Exception {
        List<String> bad = new ArrayList<>();
        for (int drop : new int[] {1, 9}) {
            Path spec = copy(J8SSupport.bankSpec("m1c-drop3", 3));
            SpecificationFile.Loaded a = load(spec);
            Path b = J8SSupport.bankSpec("m1c-drop" + drop, drop);
            byte[] bBytes = Files.readAllBytes(b);
            SpecificationFile.Loaded bl = load(b);
            assertEquals(a.lines(), bl.lines());
            assertEquals(a.duplicates(), bl.duplicates());
            Files.write(spec, bBytes);
            J8SSupport.Out seq = J8SSupport.launch(J8SSupport.BANK, false, "--spec", b.toString());
            Path d = J8SApiTest.dir("m1c-" + drop);
            String tag = UUID.randomUUID().toString();
            SplitCoordinator.Result r = run(d, a, 1, 2, (x, id) -> J8SApiTest.realWorker(x, id, J8SSupport.BANK, tag));
            SplitCoordinator.Online on = r.online();
            String named = Files.exists(d.resolve("run.json"))
                    ? JsonParser.parseString(Files.readString(d.resolve("run.json"))).getAsJsonObject()
                            .getAsJsonObject("spec").get("sha256").getAsString() : null;
            List<String> left = J8SApiTest.leftovers(tag);
            System.out.println("J8S M1c drop " + drop + ": failure " + r.failure() + "; online " + on
                    + "; run.json sha256 " + named + " (B " + sha256(bBytes) + "); sequential at: " + seq.field("at")
                    + "; snapshots " + snapshots(d) + "; leftovers " + left);
            String p = "B without " + drop + ": ";
            if (r.failure() != null) {
                bad.add(p + "failure " + r.failure());
                continue;
            }
            if (!sha256(bBytes).equals(named)) {
                bad.add(p + "run.json names " + named + ", not B's " + sha256(bBytes));
            }
            if (!on.miss() || on.firstMissIteration() != drop || !J8SSupport.bankLine(drop).equals(on.witness())
                    || on.uncoveredTotal() != 1L || on.uncoveredDistinct() != 1) {
                bad.add(p + "online " + on);
            }
            String at = seq.field("at");
            if (at == null || !at.startsWith("iteration " + drop + ", " + on.answer() + " ")) {
                bad.add(p + "answer " + on.answer() + " vs sequential at: " + at);
            }
            if (!snapshots(d).isEmpty()) {
                bad.add(p + "snapshot left: " + snapshots(d));
            }
            if (!left.isEmpty()) {
                bad.add(p + "leftovers " + left);
            }
        }
        bad.forEach(x -> System.out.println("PROBLEM " + x));
        assertEquals(List.of(), bad);
    }

    /**
     * M1 (d) after an abort that kills a worker inside its snapshot. A large but valid spec (bank2-multi plus
     * 200000 repeated lines, counted as duplicates) makes the snapshot's load take long. Worker 0 is
     * {@link J8SnapshotAbortWorker}: it waits until worker 1's {@code spec-worker-1-*.snapshot} exists, then
     * writes a startup fail marker. Derived requirement (round-04 prompt (d)): after the abort no
     * {@code spec-*.snapshot} file is left in the run directory. The coordinator ends workers with
     * {@code destroyForcibly}, which skips the worker's {@code finally}; this case checks whether the copy
     * survives.
     */
    @Test
    void abortWhileWorkerSnapshots_noSnapshotLeft() throws Exception {
        List<String> lines = new ArrayList<>(Files.readAllLines(J8SSupport.bankSpec("multi", 3, 5, 9, 12)));
        String dup = lines.get(1);
        for (int i = 0; i < 200_000; i++) {
            lines.add(dup);
        }
        Path spec = J8SSupport.DIR.resolve("m1d-big-" + UUID.randomUUID() + ".jsonl");
        Files.write(spec, lines, StandardCharsets.UTF_8);
        long t0 = System.nanoTime();
        SpecificationFile.Loaded loaded = load(spec);
        long loadMs = (System.nanoTime() - t0) / 1_000_000;
        Path d = J8SApiTest.dir("m1d");
        String tag = UUID.randomUUID().toString();
        SplitCoordinator.Result r = run(d, loaded, 1, 2, (x, id) -> id == 0
                ? List.of(J8SApiTest.JAVA, "-Dj8.tag=" + tag, "-cp", J8SApiTest.CP, J8SnapshotAbortWorker.class.getName(),
                        x.toString(), "0", "spec-worker-1-")
                : J8SApiTest.realWorker(x, id, J8SSupport.BANK, tag));
        List<String> left = J8SApiTest.leftovers(tag);
        System.out.println("J8S M1d: load " + loadMs + " ms (" + loaded.lines() + " lines, " + loaded.duplicates()
                + " duplicates); failure " + r.failure() + "; snapshots " + snapshots(d) + "; leftovers " + left);
        assertNotNull(r.failure());
        assertTrue(r.failure().contains("tester abort while worker 1 snapshots"), r.failure());
        assertEquals(List.of(), left);
        assertEquals(List.of(), snapshots(d), "snapshot files left after the abort");
    }
}
