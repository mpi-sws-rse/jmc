package org.mpi_sws.jmc.strategies.must.split;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.mp.summary.Summary;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.LongFunction;
import java.util.regex.Pattern;

/**
 * The coordinator of a split run: runs the root to the split boundary in its own JVM, describes the
 * run in {@code run.json}, starts {@code W} long-lived worker JVMs ({@link SplitWorker}), hands out
 * the items {@code N − 1} down to {@code 0} one at a time, and merges the results.
 *
 * <p><b>Assignment.</b> Each worker has at most one outstanding assignment, published atomically as
 * {@code assigned/<j>.<a>.json} (item, attempt, worker, run id). A result counts only when {@code
 * item-<j>.json} exists (the worker's rename is the completion marker) and its envelope's run id,
 * fingerprint, item, attempt and worker equal the outstanding assignment's; a mismatching envelope is a
 * semantic failure. Each result is validated (names within the table, digests well-formed) and
 * committed once.
 *
 * <p><b>Failures.</b> A fail marker of any kind ({@code worker-<id>.fail.json}, {@code
 * fail-<j>.<a>.json}) aborts the run, never retried. A worker process that exits with an outstanding
 * assignment and neither its result nor a fail marker is a process death: the item is retried exactly
 * once (attempt 1, on a live worker or a restarted one; attempt 0 is first marked consumed, so no worker
 * executes it again); a second death of the same item aborts. A
 * worker that exits after publishing its result is restarted once (a fresh replay of the root); if it
 * exits again the run continues without it, and the result says so. When every item is committed the
 * coordinator writes {@code STOP} and waits for every worker to end; on abort it terminates them.
 *
 * <p><b>Merge.</b> Distinct complete and blocked graphs are the union of the outcome digests of root and
 * items; iterations and blocked iterations are summed; the summary lines are the root's, then the items'
 * in the order {@code N − 1} … {@code 0}, deduplicated on the line without its multiplicity (the first
 * occurrence kept, multiplicities summed) — the sequential run's export order.
 */
public final class SplitCoordinator {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern N_SUFFIX = Pattern.compile(",\"n\":(\\d+)}$");

    private SplitCoordinator() {}

    /**
     * What to run.
     *
     * @param programIdentity the program and configuration identity (class, arguments, model)
     * @param program main's body
     * @param roles the declared roles for export, or {@code null} to count only
     * @param visibleRoles the visible roles, or {@code null}
     * @param k the split threshold, at least 1
     * @param workers {@code W}, at least 1
     * @param timeoutSeconds the bound for the root's run, for each worker's run, and for the whole run
     * @param runDir the run directory (created; must not hold a previous run)
     * @param workerCommand the command line that starts worker {@code id} on {@code runDir}
     * @param reversedAssignment test seam: hand items out {@code 0 … N − 1} instead of the default
     *     {@code N − 1 … 0} (the merge order is the same either way)
     * @param liveness test seam: the probe that decides whether a worker process is still alive when its
     *     death is classified (default {@link Process#isAlive}); a test can delay or force the answer to
     *     reproduce an interleaving
     */
    public record Config(String programIdentity, Runnable program, List<String> roles, List<String> visibleRoles,
                         int k, int workers, long timeoutSeconds, Path runDir,
                         BiFunction<Path, Integer, List<String>> workerCommand, boolean reversedAssignment,
                         java.util.function.Predicate<Process> liveness) {

        /** A configuration with the given assignment order and the default liveness probe. */
        public Config(String programIdentity, Runnable program, List<String> roles, List<String> visibleRoles,
                      int k, int workers, long timeoutSeconds, Path runDir,
                      BiFunction<Path, Integer, List<String>> workerCommand, boolean reversedAssignment) {
            this(programIdentity, program, roles, visibleRoles, k, workers, timeoutSeconds, runDir, workerCommand,
                    reversedAssignment, Process::isAlive);
        }

        /** A configuration with the default assignment order {@code N − 1 … 0}. */
        public Config(String programIdentity, Runnable program, List<String> roles, List<String> visibleRoles,
                      int k, int workers, long timeoutSeconds, Path runDir,
                      BiFunction<Path, Integer, List<String>> workerCommand) {
            this(programIdentity, program, roles, visibleRoles, k, workers, timeoutSeconds, runDir, workerCommand,
                    false);
        }

        /** Checks the arguments. */
        public Config {
            Objects.requireNonNull(programIdentity);
            Objects.requireNonNull(program);
            Objects.requireNonNull(runDir);
            Objects.requireNonNull(workerCommand);
            Objects.requireNonNull(liveness);
            if (k < 1 || workers < 1) {
                throw new IllegalArgumentException("K and W must be at least 1, were " + k + " and " + workers);
            }
        }
    }

    /**
     * The merged result.
     *
     * @param split whether the root reached the boundary (otherwise the run is the sequential run)
     * @param r the root's finished iterations
     * @param n the items
     * @param distinctComplete distinct complete graphs
     * @param distinctBlocked distinct blocked graphs
     * @param ran iterations over root and items
     * @param blockedIterations blocked iterations over root and items
     * @param summaryLines the merged summary lines (no header); {@code null} without export
     * @param itemIterations per item index, its iterations (the item-size distribution)
     * @param workersLost workers that died twice and were not replaced
     * @param failure the abort reason, or {@code null} if the run completed
     * @param exploreNanos wall time from the root's start to the last committed result (to the root's end
     *     without a split; to the abort for an aborted run)
     * @param workerPeakRssKb the largest worker peak RSS carried by a committed result (the worker JVM's
     *     peak when it published it), -1 for none
     * @param maxima the graph-shape maxima over the root and every committed result, each carrying its
     *     worker JVM's maxima at publication (keys of {@code SplitWorker.MAXIMA}); empty for an aborted
     *     run
     */
    public record Result(boolean split, int r, int n, int distinctComplete, int distinctBlocked, int ran,
                         int blockedIterations, List<String> summaryLines, Map<Integer, Integer> itemIterations,
                         int workersLost, String failure, long exploreNanos, long workerPeakRssKb,
                         Map<String, Long> maxima) {}

    /** One worker slot. */
    private static final class Slot {
        final int id;
        Process process;
        int restarts;
        boolean lost;
        int item = -1;
        int attempt = -1;

        Slot(int id) {
            this.id = id;
        }
    }

    /**
     * Runs the split run.
     *
     * @param config what to run
     * @return the merged result; an aborted run has {@link Result#failure()} set
     * @throws IOException if the run directory cannot be prepared
     */
    public static Result run(Config config) throws IOException {
        Path dir = config.runDir();
        Files.createDirectories(dir.resolve("assigned"));
        if (Files.exists(dir.resolve("run.json"))) {
            throw new IOException(dir + " already holds a run");
        }
        long startNanos = System.nanoTime();
        Root root = runRoot(config);
        if (root.failure != null) {
            return new Result(root.split, root.rootIterations, root.entries, 0, 0, 0, 0, null, Map.of(), 0,
                    root.failure, System.nanoTime() - startNanos, -1, Map.of());
        }
        Set<String> complete = new HashSet<>();
        Set<String> blocked = new HashSet<>();
        for (ItemResult.Outcome o : root.outcomes) {
            (o.blocked() ? blocked : complete).add(o.digest());
        }
        if (!root.split) {
            return new Result(false, root.rootIterations, 0, complete.size(), blocked.size(), root.iterations,
                    root.blocked, root.summaryLines, Map.of(), 0, null, System.nanoTime() - startNanos, -1,
                    root.maxima);
        }
        String runId = UUID.randomUUID().toString();
        JsonObject runJson = new JsonObject();
        runJson.addProperty("run", runId);
        runJson.addProperty("program", config.programIdentity());
        runJson.addProperty("k", config.k());
        runJson.addProperty("r", root.rootIterations);
        runJson.addProperty("n", root.entries);
        runJson.addProperty("fingerprint", root.fingerprint);
        JsonObject names = new JsonObject();
        root.view.names().forEach(names::addProperty);
        runJson.add("names", names);
        runJson.addProperty("nextId", root.view.nextId());
        JsonArray transcript = new JsonArray();
        for (ItemResult.Outcome o : root.outcomes) {
            JsonObject x = new JsonObject();
            x.addProperty("blocked", o.blocked());
            x.addProperty("digest", o.digest());
            transcript.add(x);
        }
        runJson.add("transcript", transcript);
        if (root.summaryLines != null) {
            JsonArray lines = new JsonArray();
            root.summaryLines.forEach(lines::add);
            runJson.add("summaries", lines);
        }
        runJson.addProperty("timeoutSeconds", config.timeoutSeconds());
        writeAtomically(dir.resolve("run.json"), runJson);

        Map<Integer, JsonObject> committed = new TreeMap<>();
        int[] lostOut = new int[1];
        long[] lastCommitOut = {System.nanoTime()};
        String failure = dispatch(config, dir, runId, root, committed, lostOut, lastCommitOut);
        int lost = lostOut[0];
        if (failure != null) {
            return new Result(true, root.rootIterations, root.entries, 0, 0, 0, 0, null, Map.of(), lost, failure,
                    System.nanoTime() - startNanos, -1, Map.of());
        }
        int ran = root.iterations;
        int blockedIterations = root.blocked;
        Map<Integer, Integer> sizes = new TreeMap<>();
        LinkedHashMap<String, Integer> merged = new LinkedHashMap<>();
        if (root.summaryLines != null) {
            for (String line : root.summaryLines) {
                mergeLine(merged, line);
            }
        }
        for (int j = root.entries - 1; j >= 0; j--) {
            JsonObject env = committed.get(j);
            ran += env.get("iterations").getAsInt();
            blockedIterations += env.get("blockedIterations").getAsInt();
            sizes.put(j, env.get("iterations").getAsInt());
            for (JsonElement e : env.getAsJsonArray("outcomes")) {
                JsonObject o = e.getAsJsonObject();
                (o.get("blocked").getAsBoolean() ? blocked : complete).add(o.get("digest").getAsString());
            }
            if (env.has("summaries")) {
                for (JsonElement e : env.getAsJsonArray("summaries")) {
                    mergeLine(merged, e.getAsString());
                }
            }
        }
        List<String> lines = null;
        if (root.summaryLines != null) {
            lines = new ArrayList<>();
            for (Map.Entry<String, Integer> e : merged.entrySet()) {
                lines.add(e.getKey() + ",\"n\":" + e.getValue() + "}");
            }
        }
        long workerRss = -1;
        Map<String, Long> maxima = new TreeMap<>(root.maxima);
        for (JsonObject env : committed.values()) {
            workerRss = Math.max(workerRss, env.get("peak_rss_kb").getAsLong());
            JsonObject mx = env.getAsJsonObject("maxima");
            for (String key : SplitWorker.MAXIMA) {
                maxima.merge(key, mx.get(key).getAsLong(), Math::max);
            }
        }
        return new Result(true, root.rootIterations, root.entries, complete.size(), blocked.size(), ran,
                blockedIterations, lines,
                sizes, lost, null, lastCommitOut[0] - startNanos, workerRss, maxima);
    }

    /** Adds one summary line (with its multiplicity) to the merge, keyed by the line without it. */
    private static void mergeLine(Map<String, Integer> merged, String line) {
        java.util.regex.Matcher m = N_SUFFIX.matcher(line);
        if (!m.find()) {
            throw new IllegalStateException("summary line without a multiplicity: " + line);
        }
        merged.merge(line.substring(0, m.start()), Integer.parseInt(m.group(1)), Integer::sum);
    }

    /** The root's run and its state at the boundary. */
    private static final class Root implements MustStrategy.SplitControl {
        final Config config;
        List<ItemResult.Outcome> outcomes = new ArrayList<>();
        boolean split;
        int rootIterations;
        int entries;
        int iterations;
        int blocked;
        String fingerprint;
        MustStrategy.SplitView view;
        List<String> summaryLines;
        Map<String, Long> maxima = Map.of();
        String failure;

        Root(Config config) {
            this.config = config;
        }

        @Override
        public int splitThreshold() {
            return config.k();
        }

        @Override
        public void outcome(boolean b, GraphCanon canon, LongFunction<String> names) {
            String digest = TypedGraphEncoding.sha256(TypedGraphEncoding.outcome(canon, names));
            outcomes.add(new ItemResult.Outcome(b, digest));
        }

        @Override
        public boolean atBoundary(MustStrategy.SplitView v) {
            split = true;
            view = v;
            rootIterations = v.finishedIterations();
            entries = v.stackTopFirst().size();
            fingerprint = StackFingerprint.of(config.programIdentity(), config.k(), v, outcomes);
            return false;
        }

        @Override
        public void segmentDone(MustStrategy.Segment s) {
            iterations = s.iterations();
            blocked = s.blockedIterations();
            if (s.summaries() != null) {
                summaryLines = new ArrayList<>();
                for (Map.Entry<Summary, Integer> e : s.summaries().entrySet()) {
                    summaryLines.add(e.getKey().toJson(e.getValue()));
                }
            }
        }

        @Override
        public OptionalInt nextItem() {
            return OptionalInt.empty();
        }
    }

    /** Runs the root in this JVM. */
    private static Root runRoot(Config config) {
        Root root = new Root(config);
        MustStrategy strategy = config.roles() == null
                ? new MustStrategy(1L, "build/test-results/jmc-report")
                : new MustStrategy(1L, "build/test-results/jmc-report", config.roles(), config.visibleRoles());
        strategy.setSplitControl(root);
        MpThread.setStaleWaitMillis(ItemDriver.SPLIT_STALE_WAIT_MILLIS);
        try {
            JmcCheckerConfiguration checkerConfig = new JmcCheckerConfiguration.Builder()
                    .strategyType("must")
                    .numIterations(0)
                    .seed(1L)
                    .reportPath("build/test-results/jmc-report")
                    .strategyConstructor(c -> strategy)
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .build();
            new JmcModelChecker(checkerConfig).check(new JmcFunctionalTestTarget(config.programIdentity(),
                    () -> MpProcess.runMain(config.program())));
            root.maxima = SplitWorker.maxima(strategy);
            if (!root.split && !strategy.exhausted()) {
                root.failure = "the root's run ended before the exploration was complete";
            }
        } catch (JmcCheckerException e) {
            root.failure = e.getMessage();
        } catch (RuntimeException | Error e) {
            root.failure = e.toString();
        } finally {
            MpThread.setStaleWaitMillis(MpThread.DEFAULT_STALE_WAIT_MILLIS);
        }
        return root;
    }

    /**
     * Starts the workers, assigns every item, collects and validates the results.
     *
     * @return {@code null} when every item is committed and every worker has ended, else the abort reason
     */
    private static String dispatch(Config config, Path dir, String runId, Root root, Map<Integer, JsonObject> committed,
                                   int[] lostOut, long[] lastCommitOut) {
        List<Slot> slots = new ArrayList<>();
        try {
            for (int i = 0; i < config.workers(); i++) {
                Slot s = new Slot(i);
                slots.add(s);
                s.process = start(config, dir, i);
            }
            java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
            for (int i = 0; i < root.entries; i++) {
                int j = config.reversedAssignment() ? i : root.entries - 1 - i;
                queue.add(new int[] {j, 0});
            }
            long deadline = System.currentTimeMillis() + config.timeoutSeconds() * 1000L;
            Set<String> tableNames = root.view.names().keySet();
            boolean export = root.summaryLines != null;
            while (committed.size() < root.entries) {
                if (System.currentTimeMillis() > deadline) {
                    return abort(slots, "timed out with " + committed.size() + " of " + root.entries
                            + " items committed");
                }
                String marker = firstFailMarker(dir);
                if (marker != null) {
                    return abort(slots, failMarkerReason(dir, marker));
                }
                for (Slot s : slots) {
                    if (s.lost) {
                        continue;
                    }
                    String bad = commitIfPublished(dir, runId, root.fingerprint, s, tableNames, export, committed,
                            lastCommitOut);
                    if (bad != null) {
                        return abort(slots, bad);
                    }
                    if (!config.liveness().test(s.process)) {
                        marker = firstFailMarker(dir);
                        if (marker != null) {
                            return abort(slots, failMarkerReason(dir, marker));
                        }
                        // The result may have been renamed into place between the check above and the exit:
                        // look again now that the process is known to be dead.
                        bad = commitIfPublished(dir, runId, root.fingerprint, s, tableNames, export, committed,
                                lastCommitOut);
                        if (bad != null) {
                            return abort(slots, bad);
                        }
                        if (s.item >= 0) {
                            // A process death with the item outstanding and no result: retried once.
                            if (s.attempt >= 1) {
                                return abort(slots, "item " + s.item + " lost its worker twice");
                            }
                            // Void the dead attempt before anything else can see it: a restarted worker
                            // with the same id would otherwise find and execute the stale assignment.
                            String dead = s.item + "." + s.attempt + ".json";
                            Files.createDirectories(dir.resolve("consumed"));
                            writeAtomically(dir.resolve("consumed").resolve(dead),
                                    readJson(dir.resolve("assigned").resolve(dead)));
                            queue.addFirst(new int[] {s.item, s.attempt + 1});
                            s.item = -1;
                            s.attempt = -1;
                        }
                        if (s.restarts == 0) {
                            s.restarts++;
                            s.process = start(config, dir, s.id);
                        } else {
                            s.lost = true;
                            lostOut[0]++;
                        }
                    }
                }
                if (slots.stream().allMatch(s -> s.lost)) {
                    return abort(slots, "every worker was lost");
                }
                for (Slot s : slots) {
                    if (!s.lost && s.item < 0 && s.process.isAlive() && !queue.isEmpty()) {
                        int[] next = queue.poll();
                        s.item = next[0];
                        s.attempt = next[1];
                        JsonObject a = new JsonObject();
                        a.addProperty("run", runId);
                        a.addProperty("item", s.item);
                        a.addProperty("attempt", s.attempt);
                        a.addProperty("worker", s.id);
                        writeAtomically(dir.resolve("assigned").resolve(s.item + "." + s.attempt + ".json"), a);
                    }
                }
                sleep();
            }
            writeAtomically(dir.resolve("STOP"), new JsonObject());
            for (Slot s : slots) {
                if (!s.lost && !s.process.waitFor(Math.max(1, deadline - System.currentTimeMillis()),
                        java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    return abort(slots, "worker " + s.id + " did not stop");
                }
            }
            String marker = firstFailMarker(dir);
            if (marker != null) {
                return abort(slots, failMarkerReason(dir, marker));
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return abort(slots, "coordinator interrupted");
        } catch (IOException | RuntimeException e) {
            // Any operational failure (a worker that cannot be started, a file that cannot be written or
            // parsed) ends the run as incomplete, after every started worker has been stopped and joined.
            return abort(slots, "coordinator failure: " + e);
        } catch (Error e) {
            // Not recovered from, but no started worker may outlive the coordinator: stop and join them,
            // then let the error propagate.
            abort(slots, "coordinator error: " + e);
            throw e;
        }
    }

    /**
     * Validates and commits the slot's outstanding result if its file exists.
     *
     * @return {@code null} if nothing was outstanding, nothing was published yet, or the result was committed;
     *     otherwise the reason the result is refused
     */
    private static String commitIfPublished(Path dir, String runId, String fingerprint, Slot s, Set<String> names,
                                            boolean export, Map<Integer, JsonObject> committed,
                                            long[] lastCommitOut) {
        if (s.item < 0) {
            return null;
        }
        Path result = dir.resolve("item-" + s.item + ".json");
        if (!Files.exists(result)) {
            return null;
        }
        JsonObject env = readJson(result);
        String bad = validate(env, runId, fingerprint, s, names, export);
        if (bad != null) {
            return "item " + s.item + ": " + bad;
        }
        committed.put(s.item, env);
        lastCommitOut[0] = System.nanoTime();
        s.item = -1;
        s.attempt = -1;
        return null;
    }

    /** The abort reason for a fail marker. */
    private static String failMarkerReason(Path dir, String marker) {
        return "fail marker " + marker + ": " + readJson(dir.resolve(marker)).get("reason");
    }

    /** Checks a result envelope against the outstanding assignment. */
    private static String validate(JsonObject env, String runId, String fingerprint, Slot s, Set<String> names,
                                   boolean export) {
        if (!runId.equals(str(env, "run")) || !fingerprint.equals(str(env, "fingerprint"))
                || !env.has("item") || env.get("item").getAsInt() != s.item
                || !env.has("attempt") || env.get("attempt").getAsInt() != s.attempt
                || !env.has("worker") || env.get("worker").getAsInt() != s.id) {
            return "envelope " + env.get("run") + "/" + env.get("item") + "/" + env.get("attempt") + "/"
                    + env.get("worker") + " does not match the assignment " + s.item + "/" + s.attempt + "/" + s.id;
        }
        if (export != env.has("summaries")) {
            // An exporting run needs every item's summaries; a counting run has none.
            return export ? "the result carries no summaries, but the run exports"
                    : "the result carries summaries, but the run only counts";
        }
        for (JsonElement e : env.getAsJsonArray("names")) {
            if (!names.contains(e.getAsString())) {
                return "process name " + e.getAsString() + " is not in the frozen table";
            }
        }
        if (!env.has("maxima") || !env.get("maxima").isJsonObject()) {
            return "the result carries no graph-shape maxima";
        }
        // -1 is the worker's "unavailable" value for the peak RSS.
        if (!integral(env.get("peak_rss_kb"), -1)) {
            return "the result's peak RSS is missing, not an integer, or below -1";
        }
        for (String key : SplitWorker.MAXIMA) {
            if (!integral(env.getAsJsonObject("maxima").get(key), 0)) {
                return "the result's maximum " + key + " is missing, not an integer, or negative";
            }
        }
        for (JsonElement e : env.getAsJsonArray("outcomes")) {
            if (!DIGEST.matcher(e.getAsJsonObject().get("digest").getAsString()).matches()) {
                return "malformed digest " + e;
            }
        }
        return null;
    }

    /** Whether v is a JSON number that is an exact long of at least min (no fraction, no overflow). */
    private static boolean integral(JsonElement v, long min) {
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        try {
            return v.getAsBigDecimal().longValueExact() >= min;
        } catch (ArithmeticException | NumberFormatException e) {
            return false;
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : null;
    }

    /** Terminates every live worker, waits for them, and returns the reason. */
    private static String abort(List<Slot> slots, String reason) {
        for (Slot s : slots) {
            if (s.process != null && s.process.isAlive()) {
                s.process.destroyForcibly();
            }
        }
        boolean interrupted = false;
        for (Slot s : slots) {
            while (s.process != null) {
                try {
                    s.process.waitFor();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return reason;
    }

    /** The name of the first fail marker in the run directory, or {@code null}. */
    private static String firstFailMarker(Path dir) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(".fail.json") || (f.startsWith("fail-") && f.endsWith(".json")))
                    .sorted().findFirst().orElse(null);
        }
    }

    private static Process start(Config config, Path dir, int id) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(config.workerCommand().apply(dir, id));
        pb.redirectErrorStream(true);
        pb.redirectOutput(dir.resolve("worker-" + id + ".log").toFile());
        return pb.start();
    }

    private static void sleep() throws InterruptedException {
        Thread.sleep(SplitWorker.POLL_MILLIS);
    }

    /**
     * Writes a JSON value to a temporary file in the same directory and renames it into place.
     *
     * @param target the file
     * @param value the value
     */
    static void writeAtomically(Path target, JsonElement value) {
        try {
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
            Files.writeString(tmp, GSON.toJson(value), StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("cannot write " + target + ": " + e, e);
        }
    }

    /**
     * Reads a JSON object.
     *
     * @param file the file
     * @return its object
     * @throws IllegalStateException if it cannot be read or is not an object
     */
    static JsonObject readJson(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("cannot read " + file + ": " + e, e);
        }
    }
}
