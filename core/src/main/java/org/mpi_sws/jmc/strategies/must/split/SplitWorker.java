package org.mpi_sws.jmc.strategies.must.split;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongFunction;
import java.util.stream.Stream;

/**
 * A worker of a split run: one JVM that replays the root to the split boundary, checks that it
 * reached exactly the coordinator's state ({@code R}, {@code N}, fingerprint, name table), and then
 * explores the items the coordinator assigns it, one at a time, publishing each result as a file.
 *
 * <p>Files in the run directory (all written to a temporary name and renamed):
 *
 * <ul>
 *   <li>read: {@code run.json} (the coordinator's run description), {@code assigned/<j>.<a>.json} (item
 *       {@code j}, attempt {@code a}, naming the worker), {@code STOP};</li>
 *   <li>written: {@code item-<j>.json} (the result envelope: run id, fingerprint, item, attempt, worker,
 *       iterations, blocked iterations, the outcomes in order, the summary lines, the names met);
 *       {@code fail-<j>.<a>.json} (a semantic failure during item {@code j}); {@code
 *       worker-<id>.fail.json} (a startup failure: the replay did not reach the coordinator's state).</li>
 * </ul>
 *
 * <p>An assignment is consumed by its (run, item, attempt) triple, so a persisted assignment file never
 * triggers a second execution: before executing it the worker writes {@code consumed/<j>.<a>.json}, and
 * a worker restarted after a death skips every assignment already marked consumed. The worker polls
 * the run directory every 200 ms from inside the strategy's {@code initIteration}, and halts
 * successfully when it finds {@code STOP} with nothing assigned.
 *
 * <p>Fault injection, for tests only: the system property {@code jmc.split.fault}, a comma-separated
 * list of {@code <kind>:<item>:<attempt>}, is read only here and is inert when unset. Kinds: {@code
 * die-before} (the JVM exits before publishing the item's result), {@code die-after} (exits right after
 * the rename), {@code wrong-item}, {@code wrong-attempt}, {@code wrong-worker} (the envelope carries a
 * wrong value), {@code fail} (a semantic failure: the fail marker is written); and {@code
 * fingerprint:-1:-1} (the replay's fingerprint is altered, a startup failure).
 */
public final class SplitWorker implements MustStrategy.SplitControl {

    /** The polling period, in milliseconds. */
    static final long POLL_MILLIS = 200L;

    /** The exit status of a worker that died by fault injection. */
    static final int INJECTED_DEATH_STATUS = 3;

    private final Path runDir;
    private final int workerId;
    private final JsonObject run;
    private final List<String> faults;
    private final Set<String> consumed = new HashSet<>();
    private List<ItemResult.Outcome> outcomes = new ArrayList<>();
    private List<ItemResult.Outcome> rootOutcomes = List.of();
    private int currentItem = -1;
    /** Whether the replay reached the split boundary and matched the coordinator's state. */
    private boolean verified;
    private int currentAttempt = -1;

    /**
     * Opens the run.
     *
     * @param runDir the run directory
     * @param workerId this worker's id
     * @throws IOException if {@code run.json} cannot be read
     */
    public SplitWorker(Path runDir, int workerId) throws IOException {
        this.runDir = Objects.requireNonNull(runDir);
        this.workerId = workerId;
        this.run = SplitCoordinator.readJson(runDir.resolve("run.json"));
        String spec = System.getProperty("jmc.split.fault", "");
        this.faults = spec.isEmpty() ? List.of() : List.of(spec.split(","));
    }

    /** The item being explored, -1 for none. */
    int currentItem() {
        return currentItem;
    }

    /** The attempt of the item being explored, -1 for none. */
    int currentAttempt() {
        return currentAttempt;
    }

    @Override
    public int splitThreshold() {
        return run.get("k").getAsInt();
    }

    @Override
    public void outcome(boolean blocked, GraphCanon canon, LongFunction<String> names) {
        String digest = TypedGraphEncoding.sha256(TypedGraphEncoding.outcome(canon, names));
        outcomes.add(new ItemResult.Outcome(blocked, digest));
    }

    @Override
    public boolean atBoundary(MustStrategy.SplitView view) {
        // The root's segment was reported just before this call; rootOutcomes is its transcript.
        String fingerprint =
                StackFingerprint.of(run.get("program").getAsString(), splitThreshold(), view, rootOutcomes);
        if (injected("fingerprint", -1, -1)) {
            fingerprint = "0" + fingerprint.substring(1);
        }
        Map<String, Long> names = new TreeMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : run.getAsJsonObject("names").entrySet()) {
            names.put(e.getKey(), e.getValue().getAsLong());
        }
        List<String> problems = new ArrayList<>();
        if (view.finishedIterations() != run.get("r").getAsInt()) {
            problems.add("R " + view.finishedIterations() + " differs from " + run.get("r").getAsInt());
        }
        if (view.stackTopFirst().size() != run.get("n").getAsInt()) {
            problems.add("N " + view.stackTopFirst().size() + " differs from " + run.get("n").getAsInt());
        }
        if (!fingerprint.equals(run.get("fingerprint").getAsString())) {
            problems.add("fingerprint " + fingerprint + " differs from " + run.get("fingerprint").getAsString());
        }
        if (!names.equals(view.names()) || view.nextId() != run.get("nextId").getAsLong()) {
            problems.add("name table " + view.names() + " (next " + view.nextId() + ") differs from " + names);
        }
        if (!problems.isEmpty()) {
            JsonObject fail = envelopeBase();
            fail.addProperty("reason", "replay did not reach the coordinator's state: " + String.join("; ", problems));
            SplitCoordinator.writeAtomically(runDir.resolve("worker-" + workerId + ".fail.json"), fail);
            throw new IllegalStateException("worker " + workerId + ": " + String.join("; ", problems));
        }
        verified = true;
        return true;
    }

    @Override
    public void segmentDone(MustStrategy.Segment s) {
        List<ItemResult.Outcome> done = outcomes;
        outcomes = new ArrayList<>();
        if (s.index() < 0) {
            rootOutcomes = done;
            return;
        }
        int j = s.index();
        int attempt = currentAttempt;
        if (injected("fail", j, attempt)) {
            throw new IllegalStateException("injected semantic failure in item " + j);
        }
        if (injected("die-before", j, attempt)) {
            Runtime.getRuntime().halt(INJECTED_DEATH_STATUS);
        }
        JsonObject env = envelopeBase();
        env.addProperty("item", injected("wrong-item", j, attempt) ? j + 1 : j);
        env.addProperty("attempt", injected("wrong-attempt", j, attempt) ? attempt + 1 : attempt);
        if (injected("wrong-worker", j, attempt)) {
            env.addProperty("worker", workerId + 1);
        }
        env.addProperty("iterations", s.iterations());
        env.addProperty("blockedIterations", s.blockedIterations());
        JsonArray outs = new JsonArray();
        for (ItemResult.Outcome o : done) {
            JsonObject x = new JsonObject();
            x.addProperty("blocked", o.blocked());
            x.addProperty("digest", o.digest());
            outs.add(x);
        }
        env.add("outcomes", outs);
        if (s.summaries() != null) {
            JsonArray lines = new JsonArray();
            for (Map.Entry<Summary, Integer> e : s.summaries().entrySet()) {
                lines.add(e.getKey().toJson(e.getValue()));
            }
            env.add("summaries", lines);
        }
        JsonArray names = new JsonArray();
        s.names().forEach(names::add);
        env.add("names", names);
        Path target = runDir.resolve("item-" + j + ".json");
        if (Files.exists(target)) {
            throw new IllegalStateException("item-" + j + ".json already exists; a result is never overwritten");
        }
        SplitCoordinator.writeAtomically(target, env);
        if (injected("die-after", j, attempt)) {
            Runtime.getRuntime().halt(INJECTED_DEATH_STATUS);
        }
        currentItem = -1;
        currentAttempt = -1;
    }

    @Override
    public OptionalInt nextItem() {
        while (true) {
            Path assigned = runDir.resolve("assigned");
            if (Files.isDirectory(assigned)) {
                try (Stream<Path> files = Files.list(assigned)) {
                    for (Path p : (Iterable<Path>) files.sorted()::iterator) {
                        String f = p.getFileName().toString();
                        if (!f.endsWith(".json") || consumed.contains(f)) {
                            continue;
                        }
                        JsonObject a = SplitCoordinator.readJson(p);
                        if (a.get("worker").getAsInt() != workerId
                                || !a.get("run").getAsString().equals(run.get("run").getAsString())) {
                            continue;
                        }
                        consumed.add(f);
                        // Consumption is durable: a restarted worker (same id, fresh memory) must not
                        // execute this (run, item, attempt) again.
                        Path marker = runDir.resolve("consumed").resolve(f);
                        if (Files.exists(marker)
                                || Files.exists(runDir.resolve("item-" + a.get("item").getAsInt() + ".json"))) {
                            continue;
                        }
                        Files.createDirectories(marker.getParent());
                        SplitCoordinator.writeAtomically(marker, a);
                        currentItem = a.get("item").getAsInt();
                        currentAttempt = a.get("attempt").getAsInt();
                        return OptionalInt.of(currentItem);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("cannot read the assignments: " + e, e);
                }
            }
            if (Files.exists(runDir.resolve("STOP"))) {
                return OptionalInt.empty();
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("worker interrupted while waiting for an assignment", e);
            }
        }
    }

    /**
     * Runs a worker to the end.
     *
     * @param runDir the run directory
     * @param workerId this worker's id
     * @param program main's body
     * @param roles the declared roles for export, or {@code null} to count only
     * @param visibleRoles the visible roles, or {@code null}
     * @return {@code 0} when the coordinator stopped it after its assignments, {@code 1} on a failure
     *     (a fail marker has been written)
     */
    public static int run(Path runDir, int workerId, Runnable program, List<String> roles, List<String> visibleRoles) {
        SplitWorker worker;
        try {
            worker = new SplitWorker(runDir, workerId);
        } catch (IOException e) {
            System.err.println("worker " + workerId + ": cannot open " + runDir + ": " + e);
            return 1;
        }
        MustStrategy strategy = roles == null
                ? new MustStrategy(1L, "build/test-results/jmc-report")
                : new MustStrategy(1L, "build/test-results/jmc-report", roles, visibleRoles);
        strategy.setSplitControl(worker);
        String failure = null;
        MpThread.setStaleWaitMillis(ItemDriver.SPLIT_STALE_WAIT_MILLIS);
        try {
            JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                    .strategyType("must")
                    .numIterations(0)
                    .seed(1L)
                    .reportPath("build/test-results/jmc-report")
                    .strategyConstructor(c -> strategy)
                    .timeout(Duration.ofSeconds(worker.run.get("timeoutSeconds").getAsLong()))
                    .build();
            new JmcModelChecker(config).check(new JmcFunctionalTestTarget(worker.run.get("program").getAsString(),
                    () -> MpProcess.runMain(program)));
            if (!worker.verified) {
                // The replay ran out of work (or ended) before the coordinator's boundary: it diverged.
                failure = "the replay ended before reaching the split boundary (R "
                        + worker.run.get("r").getAsInt() + ", N " + worker.run.get("n").getAsInt() + ")";
            } else if (!strategy.exhausted()) {
                failure = "the worker's run ended before it was stopped";
            }
        } catch (JmcCheckerException e) {
            failure = e.getMessage();
        } catch (RuntimeException | Error e) {
            failure = e.toString();
        }
        if (failure == null) {
            return 0;
        }
        if (worker.currentItem >= 0) {
            JsonObject fail = worker.envelopeBase();
            fail.addProperty("item", worker.currentItem);
            fail.addProperty("attempt", worker.currentAttempt);
            fail.addProperty("reason", failure);
            SplitCoordinator.writeAtomically(
                    runDir.resolve("fail-" + worker.currentItem + "." + worker.currentAttempt + ".json"), fail);
        } else if (!Files.exists(runDir.resolve("worker-" + workerId + ".fail.json"))) {
            JsonObject fail = worker.envelopeBase();
            fail.addProperty("reason", failure);
            SplitCoordinator.writeAtomically(runDir.resolve("worker-" + workerId + ".fail.json"), fail);
        }
        System.err.println("worker " + workerId + ": " + failure);
        return 1;
    }

    /** The fields every file this worker writes carries. */
    private JsonObject envelopeBase() {
        JsonObject o = new JsonObject();
        o.addProperty("run", run.get("run").getAsString());
        o.addProperty("fingerprint", run.get("fingerprint").getAsString());
        o.addProperty("worker", workerId);
        return o;
    }

    /** Whether the fault {@code kind:item:attempt} is injected. */
    private boolean injected(String kind, int item, int attempt) {
        return faults.contains(kind + ":" + item + ":" + attempt);
    }
}
