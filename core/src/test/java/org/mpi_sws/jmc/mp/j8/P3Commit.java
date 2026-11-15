package org.mpi_sws.jmc.mp.j8;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mpi_sws.jmc.mp.summary.SpecificationFile;
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * P3 tester round 01 (plan/jmc-must/P3-PROPOSAL.md rev 3 §2 B-lite.1, §4.2): drives the coordinator's private
 * commit step {@code SplitCoordinator.commitIfPublished} by reflection, so that the run's digest unions, the
 * {@code committed} map and the slot are observable at the commit boundary (no production seam exposes them:
 * they are locals of {@code SplitCoordinator.run}). One instance is one outstanding assignment: slot 0, item
 * {@code item}, attempt 0, run id {@link #RUN}, fingerprint {@link #FP}, name table {@code {m}}.
 *
 * <p>Needs the P3 signature {@code (Path, String, String, Slot, Set, boolean, Map, long[], Checks, Unions)};
 * on 4ce59e76 (no {@code Unions}) the constructor fails with {@link ClassNotFoundException}, so every test
 * built on this harness is a change test by construction.
 */
public final class P3Commit {

    /** The run id of the crafted envelopes. */
    public static final String RUN = "run-p3-tester";
    /** The fingerprint of the crafted envelopes (the coordinator compares it as a string). */
    public static final String FP = "e".repeat(64);
    /** Well-formed digests, distinct from any real outcome digest. */
    public static final String A = "a".repeat(64);
    public static final String B = "b".repeat(64);
    public static final String C = "c".repeat(64);
    public static final String D = "d".repeat(64);

    private static final String OUTER = "org.mpi_sws.jmc.strategies.must.split.SplitCoordinator";

    public final Path dir;
    public final int item;
    public final boolean export;
    /** The run's complete-digest union, handed to the coordinator (it adds to this very set). */
    public final Set<String> complete = new HashSet<>();
    /** The run's blocked-digest union. */
    public final Set<String> blocked = new HashSet<>();
    /** The coordinator's committed map. */
    public final Map<Integer, JsonObject> committed = new TreeMap<>();
    public final long[] lastCommit = {0L};
    private final Object slot;
    private final Object checks;
    private final Object unions;
    private final Method commit;
    private final Field slotItem;
    private final Field slotAttempt;

    /**
     * An outstanding assignment of {@code item} to slot 0.
     *
     * @param dir the run directory (created)
     * @param item the item
     * @param export whether the run exports (then a result must carry {@code summaries})
     * @param spec the online check's specification, or {@code null} for none
     * @param startMillis the run's start (for the online timestamp check)
     */
    public P3Commit(Path dir, int item, boolean export, SpecificationFile.Loaded spec, long startMillis)
            throws Exception {
        this.dir = dir;
        this.item = item;
        this.export = export;
        Files.createDirectories(dir);
        Class<?> slotC = Class.forName(OUTER + "$Slot");
        Class<?> checksC = Class.forName(OUTER + "$Checks");
        Class<?> unionsC = Class.forName(OUTER + "$Unions");
        Constructor<?> sk = slotC.getDeclaredConstructor(int.class);
        sk.setAccessible(true);
        slot = sk.newInstance(0);
        slotItem = slotC.getDeclaredField("item");
        slotAttempt = slotC.getDeclaredField("attempt");
        slotItem.setAccessible(true);
        slotAttempt.setAccessible(true);
        slotItem.setInt(slot, item);
        slotAttempt.setInt(slot, 0);
        Constructor<?> ck = checksC.getDeclaredConstructor(SpecificationFile.Loaded.class, long.class);
        ck.setAccessible(true);
        checks = ck.newInstance(spec, startMillis);
        Constructor<?> uk = unionsC.getDeclaredConstructor(Set.class, Set.class);
        uk.setAccessible(true);
        unions = uk.newInstance(complete, blocked);
        commit = SplitCoordinator.class.getDeclaredMethod("commitIfPublished", Path.class, String.class, String.class,
                slotC, Set.class, boolean.class, Map.class, long[].class, checksC, unionsC);
        commit.setAccessible(true);
    }

    /**
     * Runs the commit step once.
     *
     * @return the coordinator's answer: {@code null} (committed, or nothing published), else the refusal
     * @throws Throwable what the step threw (unwrapped)
     */
    public String commit() throws Throwable {
        try {
            return (String) commit.invoke(null, dir, RUN, FP, slot, Set.of("m"), export, committed, lastCommit, checks,
                    unions);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** The slot's outstanding item (-1 once committed). */
    public int slotItem() throws IllegalAccessException {
        return slotItem.getInt(slot);
    }

    /** The slot's outstanding attempt (-1 once committed). */
    public int slotAttempt() throws IllegalAccessException {
        return slotAttempt.getInt(slot);
    }

    /** One outcome object. */
    public static JsonObject outcome(boolean blocked, String digest) {
        JsonObject o = new JsonObject();
        o.addProperty("blocked", blocked);
        o.addProperty("digest", digest);
        return o;
    }

    /** An outcome given as raw JSON text. */
    public static JsonElement raw(String json) {
        return JsonParser.parseString(json);
    }

    /**
     * An envelope that passes every check of {@code validate} for this assignment (and carries no online
     * object), with the given outcomes; iterations and blocked iterations as given.
     */
    public JsonObject envelope(JsonArray outcomes, int iterations, int blockedIterations) {
        JsonObject env = new JsonObject();
        env.addProperty("run", RUN);
        env.addProperty("fingerprint", FP);
        env.addProperty("worker", 0);
        env.addProperty("item", item);
        env.addProperty("attempt", 0);
        env.addProperty("iterations", iterations);
        env.addProperty("blockedIterations", blockedIterations);
        env.add("outcomes", outcomes);
        if (export) {
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
        return env;
    }

    /** Publishes {@code item-<item>.json} (temporary file and rename, as a worker does). */
    public void publish(JsonObject env) throws Exception {
        Path tmp = dir.resolve("item-" + item + ".json.tmp-p3");
        Files.writeString(tmp, env.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, dir.resolve("item-" + item + ".json"), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    /** The published file, read back. */
    public JsonObject published() throws Exception {
        return JsonParser.parseString(Files.readString(dir.resolve("item-" + item + ".json"))).getAsJsonObject();
    }

    /** A fresh run directory under the J8 tester directory. */
    public static Path freshDir(String name) {
        return J8Launch.DIR.resolve("p3-" + name + "-" + System.currentTimeMillis() + "-" + System.nanoTime());
    }

    /** A JSON array of the given elements. */
    public static JsonArray array(JsonElement... es) {
        JsonArray a = new JsonArray();
        for (JsonElement e : es) {
            a.add(e);
        }
        return a;
    }
}
