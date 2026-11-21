package org.mpi_sws.jmc.mp.j8s;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * J8S round 02 (tester): a fake worker for an online split run. For every assignment naming its id it
 * publishes {@code item-<j>.json}: run id and fingerprint from {@code run.json}, the assignment's item,
 * attempt and worker, {@code iterations} as given, one well-formed outcome digest, empty summaries, names
 * {@code [m]}, peak RSS 1, maxima 0, and the {@code online} object read verbatim from a file
 * ({@code @NOW@} replaced by the current epoch ms; the text {@code MISSING} leaves the object out). It exits 0
 * when {@code STOP} appears.
 *
 * <p>Round 03: an optional fifth argument names an envelope field ({@code run} or {@code fingerprint})
 * that is written as an explicit JSON {@code null}.
 *
 * <p>Usage: {@code J8OnlineFakeWorker <runDir> <workerId> <onlineJsonFile> <iterations> [nullField]}.
 */
public final class J8OnlineFakeWorker {

    private J8OnlineFakeWorker() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        int id = Integer.parseInt(args[1]);
        String online = Files.readString(Path.of(args[2]), StandardCharsets.UTF_8).trim();
        int iterations = Integer.parseInt(args[3]);
        String nullField = args.length > 4 ? args[4] : null;
        JsonObject run = JsonParser.parseString(Files.readString(dir.resolve("run.json"))).getAsJsonObject();
        Set<String> done = new HashSet<>();
        while (!Files.exists(dir.resolve("STOP"))) {
            Path assigned = dir.resolve("assigned");
            if (Files.isDirectory(assigned)) {
                try (Stream<Path> files = Files.list(assigned)) {
                    for (Path p : (Iterable<Path>) files::iterator) {
                        String f = p.getFileName().toString();
                        if (!f.endsWith(".json") || done.contains(f)) {
                            continue;
                        }
                        JsonObject a = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                        if (a.get("worker").getAsInt() != id) {
                            continue;
                        }
                        done.add(f);
                        int j = a.get("item").getAsInt();
                        JsonObject env = new JsonObject();
                        env.addProperty("run", run.get("run").getAsString());
                        env.addProperty("fingerprint", run.get("fingerprint").getAsString());
                        env.addProperty("worker", id);
                        env.addProperty("item", j);
                        env.addProperty("attempt", a.get("attempt").getAsInt());
                        env.addProperty("iterations", iterations);
                        env.addProperty("blockedIterations", 0);
                        JsonArray outs = new JsonArray();
                        for (int i = 0; i < iterations; i++) {
                            JsonObject o = new JsonObject();
                            o.addProperty("blocked", false);
                            o.addProperty("digest", String.format("%064x", i + 1));
                            outs.add(o);
                        }
                        env.add("outcomes", outs);
                        env.add("summaries", new JsonArray());
                        JsonArray names = new JsonArray();
                        names.add("m");
                        env.add("names", names);
                        env.addProperty("peak_rss_kb", 1);
                        JsonObject mx = new JsonObject();
                        for (String k : new String[] {"max_message_events", "max_porf_pairs", "max_graph_events",
                            "max_visible_events", "max_summary_bytes", "max_porf_count"}) {
                            mx.addProperty(k, 0);
                        }
                        env.add("maxima", mx);
                        if (nullField != null) {
                            env.add(nullField, com.google.gson.JsonNull.INSTANCE);
                        }
                        String text = env.toString();
                        if (!online.equals("MISSING")) {
                            text = text.substring(0, text.length() - 1) + ",\"online\":"
                                    + online.replace("@NOW@", Long.toString(System.currentTimeMillis())) + "}";
                        }
                        Path tmp = dir.resolve("item-" + j + ".json.tmp-fake");
                        Files.writeString(tmp, text, StandardCharsets.UTF_8);
                        Files.move(tmp, dir.resolve("item-" + j + ".json"), StandardCopyOption.ATOMIC_MOVE);
                    }
                }
            }
            Thread.sleep(50);
        }
    }
}
