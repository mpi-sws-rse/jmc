package org.mpi_sws.jmc.mp.witness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.mp.witness.WitnessSupport.Out;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tests of the launcher's {@code --replay}: every graph of a store reproduces (choices, blocked executions, daemons,
 * opaque invisible values, the online bank); a graph changed in a choice, a value or its events deviates with the
 * right kind and position; the refusals (another tool, another format, another program, an option such as
 * {@code --split}, an unknown source or another commit without the allowing flag) exit 2; the report file. The
 * source comparison covers the {@code files} map only, not the manifest's {@code root}.
 */
class GraphReplayTest {

    private static final String TINY = WitnessSupport.PKG + "WitnessTiny";

    /** The store of an export of {@code program} in {@code dir}: its graph lines. */
    private static List<String> store(Path dir, String program) throws Exception {
        Out o = WitnessSupport.launch(dir, program, "must", "0", "1", "rep", "export.jsonl", "300", "--graphs",
                "s.jsonl");
        assertEquals(0, o.exit(), o.text());
        List<String> l = Files.readAllLines(dir.resolve("s.jsonl"), StandardCharsets.UTF_8);
        return l.subList(1, l.size());
    }

    private static Out replay(Path dir, String graph, String... extra) throws Exception {
        Path g = dir.resolve("g-" + Math.abs(graph.hashCode()) + ".json");
        Files.writeString(g, graph + "\n", StandardCharsets.UTF_8);
        List<String> a = new ArrayList<>(List.of("--replay", g.getFileName().toString()));
        a.addAll(List.of(extra));
        return WitnessSupport.launch(dir, a.toArray(new String[0]));
    }

    private static String replayLine(Out o) {
        for (String l : o.stdout().split("\n")) {
            if (l.startsWith("replay: ")) {
                return l;
            }
        }
        return null;
    }

    @Test
    void everyGraphOfAStore_reproduces() throws Exception {
        for (String program : List.of(TINY, WitnessSupport.PKG + "WitnessBlocked",
                WitnessSupport.PKG + "WitnessUnwritable:untagged", WitnessSupport.PKG + "WitnessUnwritable:daemon",
                WitnessSupport.BANK + ":3")) {
            Path dir = WitnessSupport.fresh("replay-all-" + program.replaceAll("[^A-Za-z0-9]", "_"));
            List<String> graphs = store(dir, program);
            assertTrue(!graphs.isEmpty(), program);
            for (String g : graphs) {
                // The test classes directory has no manifest: the source is unknown on both sides.
                Out o = replay(dir, g, "--allow-source-change");
                assertEquals(0, o.exit(), program + "\n" + g + "\n" + o.text());
                assertTrue(replayLine(o).startsWith("replay: reproduced ("), o.stdout());
                assertTrue(o.stdout().contains("\nreplay_events: "), o.stdout());
            }
        }
    }

    @Test
    void changedGraphs_deviate() throws Exception {
        Path dir = WitnessSupport.fresh("replay-deviate");
        String g = store(dir, TINY).get(0);
        // Main's first event after BEGIN is its two-option choice: flipping it changes what c1 sends.
        JsonObject flip = JsonParser.parseString(g).getAsJsonObject();
        JsonObject nd = main(flip).get(1).getAsJsonObject();
        assertEquals("NONDET", nd.get("e").getAsString());
        nd.addProperty("i", 1 - nd.get("i").getAsInt());
        Out o = replay(dir, flip.toString(), "--allow-source-change", "--report", "r.json");
        assertEquals(1, o.exit(), o.text());
        assertTrue(replayLine(o).contains(": value: expected SEND("), o.stdout());
        JsonObject r = JsonParser.parseString(Files.readString(dir.resolve("r.json"))).getAsJsonObject();
        assertEquals("deviation", r.get("result").getAsString());
        assertEquals("value", r.get("kind").getAsString());
        assertEquals(List.of("replay", "graph", "result", "at", "thread", "kind", "expected", "actual", "warnings"),
                new ArrayList<>(r.keySet()));
        // An extra recorded choice where main creates its first process.
        JsonObject extra = JsonParser.parseString(g).getAsJsonObject();
        JsonArray ev = main(extra);
        JsonArray changed = new JsonArray();
        changed.add(ev.get(0));
        changed.add(JsonParser.parseString("{\"e\":\"NONDET\",\"n\":2,\"i\":0}"));
        for (int k = 1; k < ev.size(); k++) {
            changed.add(ev.get(k));
        }
        extra.getAsJsonArray("threads").get(0).getAsJsonObject().add("events", changed);
        Out x = replay(dir, extra.toString(), "--allow-source-change");
        assertEquals(1, x.exit(), x.text());
        assertTrue(replayLine(x).startsWith("replay: deviation at (0, 2) thread \"main\": "), x.stdout());
        // A reproduction's report.
        Out ok = replay(dir, g, "--allow-source-change", "--report", "ok.json");
        assertEquals(0, ok.exit(), ok.text());
        JsonObject rok = JsonParser.parseString(Files.readString(dir.resolve("ok.json"))).getAsJsonObject();
        assertEquals("reproduced", rok.get("result").getAsString());
        assertTrue(rok.get("at").isJsonNull());
        assertTrue(rok.getAsJsonArray("warnings").get(0).getAsString().startsWith("source-change"));
    }

    private static JsonObject firstSend(JsonObject g) {
        for (JsonElement t : g.getAsJsonArray("threads")) {
            for (JsonElement e : t.getAsJsonObject().getAsJsonArray("events")) {
                if (e.getAsJsonObject().get("e").getAsString().equals("SEND")) {
                    return e.getAsJsonObject();
                }
            }
        }
        throw new AssertionError("no send");
    }

    private static JsonArray main(JsonObject g) {
        return g.getAsJsonArray("threads").get(0).getAsJsonObject().getAsJsonArray("events");
    }

    @Test
    void refusals_exit2() throws Exception {
        Path dir = WitnessSupport.fresh("replay-refused");
        String g = store(dir, TINY).get(0);
        // The source is unknown: refused without the flag.
        assertEquals(2, replay(dir, g).exit());
        assertEquals(2, replay(dir, g, "--allow-source-change", "--split", "2").exit());
        assertEquals(2, replay(dir, g, "--allow-source-change", WitnessSupport.BANK + ":3").exit());
        assertEquals(0, replay(dir, g, "--allow-source-change", TINY).exit());
        JsonObject other = JsonParser.parseString(g).getAsJsonObject();
        other.getAsJsonObject("run").addProperty("tool", "traceforge");
        assertEquals(2, replay(dir, other.toString(), "--allow-source-change").exit());
        JsonObject v2 = JsonParser.parseString(g).getAsJsonObject();
        v2.addProperty("witness", 2);
        assertEquals(2, replay(dir, v2.toString(), "--allow-source-change").exit());
        JsonObject commit = JsonParser.parseString(g).getAsJsonObject();
        commit.getAsJsonObject("run").addProperty("tool_commit", "0000000000000000000000000000000000000000");
        assertEquals(2, replay(dir, commit.toString(), "--allow-source-change").exit());
        Out allowed = replay(dir, commit.toString(), "--allow-source-change", "--allow-tool-change");
        assertEquals(0, allowed.exit(), allowed.text());
        assertTrue(allowed.stderr().contains("replay: warning: tool-change"), allowed.stderr());
        JsonObject badRf = JsonParser.parseString(g).getAsJsonObject();
        for (JsonElement t : badRf.getAsJsonArray("threads")) {
            for (JsonElement e : t.getAsJsonObject().getAsJsonArray("events")) {
                JsonObject o = e.getAsJsonObject();
                if (o.get("e").getAsString().equals("RECV") && !o.get("rf").isJsonNull()) {
                    o.add("rf", JsonParser.parseString("[0,0]"));
                }
            }
        }
        assertEquals(2, replay(dir, badRf.toString(), "--allow-source-change").exit());
        // Malformed graphs, each refused before the program runs.
        List<java.util.function.Consumer<JsonObject>> breaks = List.of(
            x -> main(x).get(0).getAsJsonObject().addProperty("extra", 1),
            x -> x.getAsJsonArray("threads").get(0).getAsJsonObject().addProperty("daemon", true),
            x -> x.getAsJsonObject("run").addProperty("order", "any"),
            x -> x.getAsJsonArray("threads").get(1).getAsJsonObject().add("events", new JsonArray()),
            x -> x.getAsJsonArray("threads").get(1).getAsJsonObject().getAsJsonArray("events").set(0,
                    JsonParser.parseString("{\"e\":\"END\"}")),
            x -> main(x).set(main(x).size() - 2, JsonParser.parseString("{\"e\":\"TJOIN\",\"child\":0}")),
            x -> main(x).get(1).getAsJsonObject().addProperty("lo", 0),
            x -> main(x).add(JsonParser.parseString("{\"e\":\"ERROR\",\"msg\":\"x\"}")),
            x -> x.addProperty("witness", 4294967297L),
            x -> x.getAsJsonObject("export").getAsJsonArray("visible").add("srv"),
            x -> x.getAsJsonObject("export").getAsJsonArray("roles").add("main"),
            x -> firstSend(x).add("v", JsonParser.parseString("{\"garbage\":1}")),
            x -> firstSend(x).add("v", JsonParser.parseString("1.5")),
            x -> firstSend(x).addProperty("to", 4294967298L));
        for (int b = 0; b < breaks.size(); b++) {
            JsonObject x = JsonParser.parseString(g).getAsJsonObject();
            breaks.get(b).accept(x);
            Out o = replay(dir, x.toString(), "--allow-source-change", "--allow-tool-change");
            assertEquals(2, o.exit(), "break " + b + ": " + o.text());
        }
        // A cycle: srv's first receive reads a send that comes after srv's own later event in the sender's order is
        // not constructible here without changing names; a join that precedes the joined thread's creation is.
        JsonObject cyc = JsonParser.parseString(g).getAsJsonObject();
        JsonArray m = main(cyc);
        JsonArray reordered = new JsonArray();
        reordered.add(m.get(0));
        for (int k = 1; k < m.size(); k++) {
            if (m.get(k).getAsJsonObject().get("e").getAsString().equals("TJOIN")) {
                reordered.add(m.get(k));
            }
        }
        for (int k = 1; k < m.size(); k++) {
            if (!m.get(k).getAsJsonObject().get("e").getAsString().equals("TJOIN")) {
                reordered.add(m.get(k));
            }
        }
        // END must stay last: move it back.
        JsonElement end = null;
        for (int k = 0; k < reordered.size(); k++) {
            if (reordered.get(k).getAsJsonObject().get("e").getAsString().equals("END")) {
                end = reordered.remove(k);
                break;
            }
        }
        reordered.add(end);
        cyc.getAsJsonArray("threads").get(0).getAsJsonObject().add("events", reordered);
        Out c = replay(dir, cyc.toString(), "--allow-source-change");
        assertEquals(2, c.exit(), c.text());
        assertTrue(c.text().contains("cycle"), c.text());
    }

    /**
     * A graph exported from a classpath entry with a {@code SOURCE.md5} replays against the same entry; a different
     * {@code root} is not a source change, a different or extra file hash is (exit 2, accepted with a warning under
     * {@code --allow-source-change}), and so is a graph whose source is {@code "unknown"}.
     */
    @Test
    void sourceFilesCompared_rootIgnored() throws Exception {
        Path dir = WitnessSupport.fresh("replay-source");
        Path cls = dir.resolve("cls");
        Path pkg = cls.resolve("org/mpi_sws/jmc/mp/witness");
        Files.createDirectories(pkg);
        Path from = Path.of(GraphReplayTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve("org/mpi_sws/jmc/mp/witness");
        try (Stream<Path> s = Files.list(from)) {
            for (Path p : s.filter(p -> p.getFileName().toString().startsWith("WitnessTiny")).toList()) {
                Files.copy(p, pkg.resolve(p.getFileName()));
            }
        }
        Path manifest = cls.resolve("SOURCE.md5");
        Files.writeString(manifest, "# root benchmarks/tiny\n"
                + "0123456789abcdef0123456789abcdef  java/WitnessTiny.java\n");
        Files.setLastModifiedTime(pkg.resolve("WitnessTiny.class"), FileTime.fromMillis(1_000_000_000_000L));
        Files.setLastModifiedTime(manifest, FileTime.fromMillis(1_000_000_100_000L));
        String cp = cls + File.pathSeparator + System.getProperty("java.class.path");
        Out export = withClasspath(dir, cp, TINY, "must", "0", "1", "rep", "export.jsonl", "300", "--graphs",
                "s.jsonl");
        assertEquals(0, export.exit(), export.text());
        String g = Files.readAllLines(dir.resolve("s.jsonl"), StandardCharsets.UTF_8).get(1);
        JsonObject source = JsonParser.parseString(g).getAsJsonObject().getAsJsonObject("run")
                .getAsJsonObject("source");
        assertEquals("benchmarks/tiny", source.get("root").getAsString(), g);

        Out same = replayWith(dir, cp, g);
        assertEquals(0, same.exit(), same.text());
        assertTrue(!same.text().contains("source-change"), same.text());

        JsonObject moved = JsonParser.parseString(g).getAsJsonObject();
        moved.getAsJsonObject("run").getAsJsonObject("source").addProperty("root", "elsewhere/tiny");
        Out m = replayWith(dir, cp, moved.toString());
        assertEquals(0, m.exit(), m.text());
        assertTrue(!m.text().contains("source-change"), m.text());

        JsonObject edited = JsonParser.parseString(g).getAsJsonObject();
        edited.getAsJsonObject("run").getAsJsonObject("source").getAsJsonObject("files")
                .addProperty("java/WitnessTiny.java", "ffffffffffffffffffffffffffffffff");
        Out e = replayWith(dir, cp, edited.toString());
        assertEquals(2, e.exit(), e.text());
        assertTrue(e.text().contains("--replay refused: source files differ from the program's manifest"), e.text());
        Out ea = replayWith(dir, cp, edited.toString(), "--allow-source-change");
        assertEquals(0, ea.exit(), ea.text());
        assertTrue(ea.text().contains("replay: warning: source-change: source files differ"), ea.text());

        JsonObject extra = JsonParser.parseString(g).getAsJsonObject();
        extra.getAsJsonObject("run").getAsJsonObject("source").getAsJsonObject("files")
                .addProperty("java/Other.java", "0123456789abcdef0123456789abcdef");
        assertEquals(2, replayWith(dir, cp, extra.toString()).exit());

        JsonObject unknown = JsonParser.parseString(g).getAsJsonObject();
        unknown.getAsJsonObject("run").addProperty("source", "unknown");
        Out u = replayWith(dir, cp, unknown.toString());
        assertEquals(2, u.exit(), u.text());
        assertTrue(u.text().contains("--replay refused: source unknown"), u.text());
    }

    /** {@code --replay} of {@code graph} with classpath {@code cp}. */
    private static Out replayWith(Path dir, String cp, String graph, String... extra) throws Exception {
        Path g = dir.resolve("g-" + Math.abs(graph.hashCode()) + ".json");
        Files.writeString(g, graph + "\n", StandardCharsets.UTF_8);
        List<String> a = new ArrayList<>(List.of("--replay", g.getFileName().toString()));
        a.addAll(List.of(extra));
        return withClasspath(dir, cp, a.toArray(new String[0]));
    }

    /** The launcher with {@code args}, run in {@code dir} with classpath {@code cp}; stderr merged into stdout. */
    private static Out withClasspath(Path dir, String cp, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java", "-cp", cp,
                WitnessSupport.LAUNCHER));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true)
                .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null"))).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Out(p.waitFor(), out, "");
    }
}
