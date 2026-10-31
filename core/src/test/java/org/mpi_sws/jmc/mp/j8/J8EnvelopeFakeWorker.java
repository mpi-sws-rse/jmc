package org.mpi_sws.jmc.mp.j8;

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
 * J8 round 10 (tester): a fake worker for a counting-mode split run. For every assignment naming its id it
 * publishes {@code item-<j>.json} with an envelope that passes every check of the coordinator except the
 * fields under test (run id and fingerprint from {@code run.json}; item, attempt, worker from the
 * assignment; one outcome with a well-formed digest; names {@code [m]}), and with {@code peak_rss_kb} and
 * {@code maxima} set according to the mode; it exits 0 when {@code STOP} appears.
 *
 * <p>Modes: {@code valid} (rss 1, every maximum 0), {@code missing-maxima}, {@code missing-rss},
 * {@code string-rss} ({@code "peak_rss_kb":"12"}), {@code negative-max} ({@code max_graph_events} = -1),
 * {@code missing-one-max} (no {@code max_porf_count}); round 11: {@code maxima-array} ({@code "maxima":[]}),
 * {@code raw:rss=<literal>} and {@code raw:<maximum key>=<literal>}, which put the JSON number text
 * {@code <literal>} verbatim into that field (every other field valid: rss 1, maxima 0).
 *
 * <p>Usage: {@code J8EnvelopeFakeWorker <runDir> <workerId> <mode>}.
 */
public final class J8EnvelopeFakeWorker {

    private J8EnvelopeFakeWorker() {}

    static final String[] MAXIMA = {"max_message_events", "max_porf_pairs", "max_graph_events",
        "max_visible_events", "max_summary_bytes", "max_porf_count"};

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        int id = Integer.parseInt(args[1]);
        String mode = args[2];
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
                        env.addProperty("iterations", 1);
                        env.addProperty("blockedIterations", 0);
                        JsonArray outs = new JsonArray();
                        JsonObject o = new JsonObject();
                        o.addProperty("blocked", false);
                        o.addProperty("digest", "0".repeat(64));
                        outs.add(o);
                        env.add("outcomes", outs);
                        JsonArray names = new JsonArray();
                        names.add("m");
                        env.add("names", names);
                        if (mode.equals("string-rss")) {
                            env.addProperty("peak_rss_kb", "12");
                        } else if (!mode.equals("missing-rss")) {
                            env.addProperty("peak_rss_kb", 1);
                        }
                        if (!mode.equals("missing-maxima")) {
                            JsonObject mx = new JsonObject();
                            for (String k : MAXIMA) {
                                if (mode.equals("missing-one-max") && k.equals("max_porf_count")) {
                                    continue;
                                }
                                mx.addProperty(k, mode.equals("negative-max") && k.equals("max_graph_events") ? -1 : 0);
                            }
                            env.add("maxima", mx);
                        }
                        String text = env.toString();
                        if (mode.equals("maxima-array")) {
                            env.add("maxima", new JsonArray());
                            text = env.toString();
                        } else if (mode.startsWith("raw:")) {
                            String target = mode.substring(4, mode.indexOf('='));
                            String literal = mode.substring(mode.indexOf('=') + 1);
                            if (target.equals("rss")) {
                                env.addProperty("peak_rss_kb", "@@RAW@@");
                            } else {
                                env.getAsJsonObject("maxima").addProperty(target, "@@RAW@@");
                            }
                            text = env.toString().replace("\"@@RAW@@\"", literal);
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
