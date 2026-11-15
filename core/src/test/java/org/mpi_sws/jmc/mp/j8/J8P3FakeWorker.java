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
 * P3 tester round 01: a fake worker whose result envelopes pass every check of the coordinator's
 * {@code validate} (run id and fingerprint from {@code run.json}; item, attempt, worker from the assignment;
 * well-formed digests; names {@code [m]}; peak RSS 1; maxima 0; {@code summaries: []} when {@code run.json}
 * says the run summarises; when {@code run.json} names a specification, an online object without a miss
 * whose lookups equal the iterations, all found), with the outcomes given by the mode. It exits 0 when
 * {@code STOP} appears.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@code dups}: the root's first transcript outcome {@code (b0, d0)} as is and flipped {@code (!b0, d0)},
 *       {@code A} complete twice and blocked once, {@code B} blocked twice, {@code C} with {@code "blocked":"true"}
 *       (a string: Gson's conversion gives {@code true}), {@code D} with {@code "blocked":1} (a number: Gson's
 *       conversion gives {@code false}); 9 iterations;</li>
 *   <li>{@code malformed-null}: {@code (false, A)}, {@code {"blocked":null,"digest":B}}, {@code (false, C)};
 *       3 iterations.</li>
 * </ul>
 * The optional third argument names a file (relative to the run directory) that must exist before the
 * envelope is published (used to order the publication after another item's commit).
 *
 * <p>Usage: {@code J8P3FakeWorker <runDir> <workerId> <mode> [waitFor]}.
 */
public final class J8P3FakeWorker {

    private J8P3FakeWorker() {}

    /** The outcomes of a mode (the dups mode needs the root's transcript). */
    static JsonArray outcomes(String mode, JsonObject run) {
        JsonArray outs = new JsonArray();
        if (mode.equals("dups")) {
            JsonObject first = run.getAsJsonArray("transcript").get(0).getAsJsonObject();
            boolean b0 = first.get("blocked").getAsBoolean();
            String d0 = first.get("digest").getAsString();
            outs.add(P3Commit.outcome(b0, d0));
            outs.add(P3Commit.outcome(!b0, d0));
            outs.add(P3Commit.outcome(false, P3Commit.A));
            outs.add(P3Commit.outcome(false, P3Commit.A));
            outs.add(P3Commit.outcome(true, P3Commit.A));
            outs.add(P3Commit.outcome(true, P3Commit.B));
            outs.add(P3Commit.outcome(true, P3Commit.B));
            outs.add(P3Commit.raw("{\"blocked\":\"true\",\"digest\":\"" + P3Commit.C + "\"}"));
            outs.add(P3Commit.raw("{\"blocked\":1,\"digest\":\"" + P3Commit.D + "\"}"));
        } else if (mode.equals("malformed-null")) {
            outs.add(P3Commit.outcome(false, P3Commit.A));
            outs.add(P3Commit.raw("{\"blocked\":null,\"digest\":\"" + P3Commit.B + "\"}"));
            outs.add(P3Commit.outcome(false, P3Commit.C));
        } else {
            throw new IllegalArgumentException("mode " + mode);
        }
        return outs;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        int id = Integer.parseInt(args[1]);
        String mode = args[2];
        String waitFor = args.length > 3 ? args[3] : null;
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
                        while (waitFor != null && !Files.exists(dir.resolve(waitFor))
                                && !Files.exists(dir.resolve("STOP"))) {
                            Thread.sleep(50);
                        }
                        int j = a.get("item").getAsInt();
                        JsonArray outs = outcomes(mode, run);
                        int blocked = 0;
                        for (var e : outs) {
                            var b = e.getAsJsonObject().get("blocked");
                            if (b != null && b.isJsonPrimitive() && b.getAsBoolean()) {
                                blocked++;
                            }
                        }
                        JsonObject env = new JsonObject();
                        env.addProperty("run", run.get("run").getAsString());
                        env.addProperty("fingerprint", run.get("fingerprint").getAsString());
                        env.addProperty("worker", id);
                        env.addProperty("item", j);
                        env.addProperty("attempt", a.get("attempt").getAsInt());
                        env.addProperty("iterations", outs.size());
                        env.addProperty("blockedIterations", blocked);
                        env.add("outcomes", outs);
                        if (run.get("summarise").getAsBoolean()) {
                            env.add("summaries", new JsonArray());
                        }
                        JsonArray names = new JsonArray();
                        names.add("m");
                        env.add("names", names);
                        env.addProperty("peak_rss_kb", 1);
                        JsonObject mx = new JsonObject();
                        for (String k : J8EnvelopeFakeWorker.MAXIMA) {
                            mx.addProperty(k, 0);
                        }
                        env.add("maxima", mx);
                        if (run.has("spec")) {
                            JsonObject on = new JsonObject();
                            on.addProperty("uncovered", 0);
                            on.add("lines", new JsonArray());
                            on.addProperty("first_local", 0);
                            on.add("witness", com.google.gson.JsonNull.INSTANCE);
                            on.add("answer", com.google.gson.JsonNull.INSTANCE);
                            on.addProperty("first_at_ms", -1);
                            on.addProperty("lookups", outs.size());
                            on.addProperty("found", outs.size());
                            on.addProperty("not_found", 0);
                            on.addProperty("no_sig", 0);
                            env.add("online", on);
                        }
                        Path tmp = dir.resolve("item-" + j + ".json.tmp-fake");
                        Files.writeString(tmp, env.toString(), StandardCharsets.UTF_8);
                        Files.move(tmp, dir.resolve("item-" + j + ".json"), StandardCopyOption.ATOMIC_MOVE);
                    }
                }
            }
            Thread.sleep(50);
        }
    }
}
