package org.mpi_sws.jmc.mp.witness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.summary.RunIdentity;
import org.mpi_sws.jmc.mp.witness.WitnessSupport.Out;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Tests of the launcher's {@code --graphs} store (witness format 1): its line structure and key order, one graph per
 * distinct exported summary whose {@code line} is that summary without {@code n}, the {@code run} identity (the
 * build's commit, the program and its arguments, the source copied from {@code SOURCE.md5} or {@code "unknown"},
 * including a manifest older than the class file), the event forms (daemon, BLK, NONDET with its raw option count,
 * TCREATE and TJOIN), the limit, the refusals, and that the export is byte-identical with and without the flag.
 */
class GraphStoreTest {

    private static final String TINY = WitnessSupport.PKG + "WitnessTiny";
    private static final String BLOCKED = WitnessSupport.PKG + "WitnessBlocked";

    /** A must export of {@code program} into {@code dir/export.jsonl}, with {@code extra} arguments. */
    private static Out export(Path dir, String program, String... extra) throws Exception {
        List<String> a = new ArrayList<>(List.of(program, "must", "0", "1", "rep", "export.jsonl",
                WitnessSupport.TIMEOUT));
        a.addAll(List.of(extra));
        return WitnessSupport.launch(dir, a.toArray(new String[0]));
    }

    private static List<String> lines(Path p) throws Exception {
        return Files.readAllLines(p, StandardCharsets.UTF_8);
    }

    /** The export's body lines without their {@code n} member. */
    private static Set<String> exportLines(Path export) throws Exception {
        Set<String> out = new HashSet<>();
        List<String> l = lines(export);
        for (String s : l.subList(1, l.size())) {
            out.add(s.substring(0, s.lastIndexOf(",\"n\":")) + "}");
        }
        return out;
    }

    private static JsonObject obj(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static List<String> keys(JsonObject o) {
        return new ArrayList<>(o.keySet());
    }

    /** Checks the store against its export and returns its graph lines, parsed. */
    private static List<JsonObject> checkStore(Path store, Path export) throws Exception {
        List<String> st = lines(store);
        String header = lines(export).get(0);
        JsonObject first = obj(st.get(0));
        assertEquals(List.of("witness_store", "export", "run"), keys(first));
        assertEquals(1, first.get("witness_store").getAsInt());
        assertEquals(obj(header), first.get("export"));
        assertTrue(first.getAsJsonObject("run").get("iteration").isJsonNull());
        Set<String> seen = new HashSet<>();
        Set<String> exported = exportLines(export);
        List<JsonObject> graphs = new ArrayList<>();
        for (String s : st.subList(1, st.size())) {
            JsonObject g = obj(s);
            assertEquals(List.of("witness", "line", "export", "run", "join", "nearest", "threads"), keys(g));
            assertEquals(1, g.get("witness").getAsInt());
            String line = g.get("line").getAsString();
            assertTrue(exported.contains(line), "a store line that the export does not have: " + line);
            assertTrue(seen.add(line), "a line stored twice: " + line);
            assertEquals(obj(header), g.get("export"));
            assertTrue(g.get("join").isJsonNull());
            assertTrue(g.get("nearest").isJsonNull());
            JsonObject run = g.getAsJsonObject("run");
            assertEquals(List.of("tool", "tool_commit", "entry", "side", "args", "model", "options", "source",
                    "iteration", "producer", "order"), keys(run));
            assertTrue(run.get("iteration").getAsInt() >= 1);
            assertEquals("first", run.get("order").getAsString());
            JsonArray threads = g.getAsJsonArray("threads");
            assertEquals("main", threads.get(0).getAsJsonObject().get("name").getAsString());
            for (JsonElement t : threads) {
                JsonArray ev = t.getAsJsonObject().getAsJsonArray("events");
                assertEquals("BEGIN", ev.get(0).getAsJsonObject().get("e").getAsString());
                String last = ev.get(ev.size() - 1).getAsJsonObject().get("e").getAsString();
                assertTrue(Set.of("END", "BLK", "ERROR").contains(last), last);
            }
            graphs.add(g);
        }
        return graphs;
    }

    @Test
    void store_oneGraphPerDistinctSummary_identityAndKeyOrder() throws Exception {
        Path dir = WitnessSupport.fresh("graphs-tiny");
        Out o = export(dir, TINY, "--graphs", "export.jsonl.graphs.jsonl");
        assertEquals(0, o.exit(), o.text());
        Path store = dir.resolve("export.jsonl.graphs.jsonl");
        List<JsonObject> graphs = checkStore(store, dir.resolve("export.jsonl"));
        assertEquals(exportLines(dir.resolve("export.jsonl")).size(), graphs.size());
        assertEquals("4 graphs written, 0 refused, to export.jsonl.graphs.jsonl",
                WitnessSupport.field(o.stdout(), "store"));
        assertEquals("4", WitnessSupport.field(o.stdout(), "graphs_written"));
        assertTrue(WitnessSupport.field(o.stdout(), "graphs_ms").matches("\\d+"), o.stdout());
        assertFalse(Files.exists(dir.resolve("export.jsonl.graphs.jsonl.part")));
        JsonObject run = graphs.get(0).getAsJsonObject("run");
        assertEquals("jmc", run.get("tool").getAsString());
        assertEquals(RunIdentity.buildCommit(), run.get("tool_commit").getAsString());
        assertEquals(TINY, run.get("entry").getAsString());
        assertTrue(run.get("side").isJsonNull());
        assertEquals(0, run.getAsJsonArray("args").size());
        assertTrue(run.get("model").isJsonNull());
        assertEquals(0, run.getAsJsonObject("options").size());
        // The test classes directory has no manifest.
        assertEquals("unknown", run.get("source").getAsString());
        assertTrue(o.stderr().contains("graphs: source unknown: no SOURCE.md5"), o.stderr());
        assertEquals("export", run.get("producer").getAsString());
        // Main chooses before any spawn: its first event after BEGIN is a two-option NONDET, then three creations.
        JsonArray main = graphs.get(0).getAsJsonArray("threads").get(0).getAsJsonObject().getAsJsonArray("events");
        assertEquals(obj("{\"e\":\"NONDET\",\"n\":2,\"i\":0}"), main.get(1));
        assertEquals("TCREATE", main.get(2).getAsJsonObject().get("e").getAsString());
        assertEquals("END", main.get(main.size() - 1).getAsJsonObject().get("e").getAsString());
    }

    @Test
    void store_blockedAndDaemon() throws Exception {
        Path dir = WitnessSupport.fresh("graphs-blocked");
        Out o = export(dir, BLOCKED, "--graphs", "g.jsonl");
        assertEquals(0, o.exit(), o.text());
        List<JsonObject> graphs = checkStore(dir.resolve("g.jsonl"), dir.resolve("export.jsonl"));
        boolean sawDaemon = false;
        boolean sawBlk = false;
        for (JsonObject g : graphs) {
            for (JsonElement t : g.getAsJsonArray("threads")) {
                JsonObject th = t.getAsJsonObject();
                JsonArray ev = th.getAsJsonArray("events");
                String last = ev.get(ev.size() - 1).getAsJsonObject().get("e").getAsString();
                if (th.get("daemon").getAsBoolean()) {
                    sawDaemon = true;
                    assertTrue(th.get("name").isJsonNull(), "a daemon spawned without a role has no name: " + th);
                    assertEquals("BLK", last, "the daemon ends blocked on its second receive");
                }
                sawBlk |= last.equals("BLK") && !th.get("daemon").getAsBoolean();
            }
        }
        assertTrue(sawDaemon);
        assertTrue(sawBlk, "x = 1: w and main end blocked");
    }

    @Test
    void limit_bounds_theGraphs_zeroWritesTheIdentityOnly() throws Exception {
        Path dir = WitnessSupport.fresh("graphs-limit");
        Out zero = export(dir, TINY, "--graphs", "z.jsonl", "--graphs-limit", "0");
        assertEquals(0, zero.exit(), zero.text());
        assertEquals(1, lines(dir.resolve("z.jsonl")).size());
        Path dir1 = WitnessSupport.fresh("graphs-limit1");
        Out one = export(dir1, TINY, "--graphs", "o.jsonl", "--graphs-limit", "1");
        assertEquals(0, one.exit(), one.text());
        assertEquals(1, checkStore(dir1.resolve("o.jsonl"), dir1.resolve("export.jsonl")).size());
    }

    @Test
    void export_byteIdentical_withAndWithoutGraphs() throws Exception {
        Path a = WitnessSupport.fresh("graphs-ident-a");
        Path b = WitnessSupport.fresh("graphs-ident-b");
        assertEquals(0, export(a, WitnessSupport.BANK + ":3").exit());
        assertEquals(0, export(b, WitnessSupport.BANK + ":3", "--graphs", "s.jsonl").exit());
        assertEquals(Files.readString(a.resolve("export.jsonl")), Files.readString(b.resolve("export.jsonl")));
        List<JsonObject> graphs = checkStore(b.resolve("s.jsonl"), b.resolve("export.jsonl"));
        assertEquals(exportLines(b.resolve("export.jsonl")).size(), graphs.size());
        assertEquals(List.of("3"), new ArrayList<String>() {
            {
                for (JsonElement e : graphs.get(0).getAsJsonObject("run").getAsJsonArray("args")) {
                    add(e.getAsString());
                }
            }
        });
    }

    @Test
    void refusals_exit2() throws Exception {
        Path dir = WitnessSupport.fresh("graphs-refused");
        Files.writeString(dir.resolve("exists.jsonl"), "x\n");
        assertEquals(2, export(dir, TINY, "--graphs", "exists.jsonl").exit());
        assertEquals("x\n", Files.readString(dir.resolve("exists.jsonl")));
        assertEquals(2, WitnessSupport.launch(dir, TINY, "must", "0", "1", "rep", "-", "300", "--graphs", "g.jsonl")
                .exit());
        assertEquals(2, export(dir, TINY, "--graphs-limit", "3").exit());
        assertEquals(2, export(dir, TINY, "--graphs", "g.jsonl", "--graphs-limit", "-1").exit());
        assertEquals(2, export(dir, TINY, "--graphs", "g.jsonl", "--graphs", "h.jsonl").exit());
        assertEquals(2, export(dir, TINY, "--graphs", "export.jsonl").exit());
        assertEquals(2, export(dir, TINY, "--graphs", "g.jsonl", "--split", "2").exit());
        assertFalse(Files.exists(dir.resolve("g.jsonl")));
        // The export may not be any file the store uses: the store, its part file, its assembly file.
        for (String reserved : List.of("g.jsonl", "g.jsonl.part", "g.jsonl.tmp")) {
            List<String> a = new ArrayList<>(List.of(TINY, "must", "0", "1", "rep", reserved, "300", "--graphs",
                    "g.jsonl"));
            assertEquals(2, WitnessSupport.launch(dir, a.toArray(new String[0])).exit(), reserved);
        }
        // Nor through a symbolic link to the store's directory.
        Path real = Files.createDirectories(dir.resolve("real"));
        Files.createSymbolicLink(dir.resolve("alias"), real);
        assertEquals(2, WitnessSupport.launch(dir, TINY, "must", "0", "1", "rep", "alias/s.jsonl.part", "300",
                "--graphs", "real/s.jsonl").exit());
        assertFalse(Files.exists(real.resolve("s.jsonl.part")));
        // Through a symbolic link above directories that do not exist yet.
        assertEquals(2, WitnessSupport.launch(dir, TINY, "must", "0", "1", "rep", "alias/new/t.jsonl.part", "300",
                "--graphs", "real/new/t.jsonl").exit());
        assertFalse(Files.exists(real.resolve("new")));
        // Through a dangling symbolic link at the export's name.
        Files.createSymbolicLink(dir.resolve("dangling.jsonl"), dir.resolve("u.jsonl.part"));
        assertEquals(2, WitnessSupport.launch(dir, TINY, "must", "0", "1", "rep", "dangling.jsonl", "300",
                "--graphs", "u.jsonl").exit());
        assertFalse(Files.exists(dir.resolve("u.jsonl.part")));
        // Through a dangling symbolic link in an ancestor.
        Files.createSymbolicLink(dir.resolve("ahead"), real.resolve("later"));
        assertEquals(2, WitnessSupport.launch(dir, TINY, "must", "0", "1", "rep", "ahead/v.jsonl.part", "300",
                "--graphs", "real/later/v.jsonl").exit());
        assertFalse(Files.exists(real.resolve("later")));
        // Through a link followed by "..": the link is expanded first.
        Files.createDirectories(real.resolve("sub"));
        Files.createSymbolicLink(dir.resolve("down"), real.resolve("sub"));
        assertEquals(2, WitnessSupport.launch(dir, TINY, "must", "0", "1", "rep", "down/../w.jsonl.part", "300",
                "--graphs", "real/w.jsonl").exit());
        assertFalse(Files.exists(real.resolve("w.jsonl.part")));
    }

    /**
     * The manifest is read from the root of the classpath entry holding the program class: a copy of WitnessTiny's
     * class files in a fresh directory placed first on the classpath, with a {@code SOURCE.md5} there. A manifest
     * older than the class file makes the source unknown.
     */
    @Test
    void manifest_copied_andStaleManifestIsUnknown() throws Exception {
        Path dir = WitnessSupport.fresh("graphs-manifest");
        Path cls = dir.resolve("cls");
        Path pkg = cls.resolve("org/mpi_sws/jmc/mp/witness");
        Files.createDirectories(pkg);
        Path from = Path.of(GraphStoreTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("org/mpi_sws/jmc/mp/witness");
        try (Stream<Path> s = Files.list(from)) {
            for (Path p : s.filter(p -> p.getFileName().toString().startsWith("WitnessTiny")).toList()) {
                Files.copy(p, pkg.resolve(p.getFileName()));
            }
        }
        Path manifest = cls.resolve("SOURCE.md5");
        Files.writeString(manifest, "# root benchmarks/tiny\n"
                + "0123456789abcdef0123456789abcdef  java/WitnessTiny.java\n"
                + "fedcba9876543210fedcba9876543210  classes/org/mpi_sws/jmc/mp/witness/WitnessTiny.class\n");
        Path classFile = pkg.resolve("WitnessTiny.class");
        Files.setLastModifiedTime(classFile, FileTime.fromMillis(1_000_000_000_000L));
        Files.setLastModifiedTime(manifest, FileTime.fromMillis(1_000_000_100_000L));
        String cp = cls + File.pathSeparator + System.getProperty("java.class.path");
        JsonObject run = runWith(dir, cp, "fresh.jsonl");
        JsonObject source = run.getAsJsonObject("source");
        assertEquals("benchmarks/tiny", source.get("root").getAsString());
        assertEquals(List.of("classes/org/mpi_sws/jmc/mp/witness/WitnessTiny.class", "java/WitnessTiny.java"),
                keys(source.getAsJsonObject("files")));
        assertEquals("0123456789abcdef0123456789abcdef",
                source.getAsJsonObject("files").get("java/WitnessTiny.java").getAsString());
        Files.setLastModifiedTime(classFile, FileTime.fromMillis(1_000_000_200_000L));
        assertEquals("unknown", runWith(dir, cp, "stale.jsonl").get("source").getAsString());
        Files.setLastModifiedTime(manifest, FileTime.fromMillis(1_000_000_300_000L));
        Files.writeString(manifest, "no root line\n");
        assertEquals("unknown", runWith(dir, cp, "bad.jsonl").get("source").getAsString());
    }

    /** Exports WitnessTiny with classpath {@code cp} and returns the store's identity line's {@code run}. */
    private static JsonObject runWith(Path dir, String cp, String store) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java", "-cp", cp,
                WitnessSupport.LAUNCHER, TINY, "must", "0", "1", "rep", store + ".export", "300", "--graphs", store));
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null"))).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), out);
        JsonObject first = obj(lines(dir.resolve(store)).get(0));
        return first.getAsJsonObject("run");
    }
}
