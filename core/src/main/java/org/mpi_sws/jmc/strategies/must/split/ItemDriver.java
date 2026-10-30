package org.mpi_sws.jmc.strategies.must.split;

import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.mp.summary.Summary;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * Runs a split run of the Must strategy inside one JVM: the root up to the split boundary, then
 * the items, one after another, each in a fresh exploration stack. It validates the decomposition
 * without any process: the root's outcomes followed by the items' (in the order {@code N − 1} down
 * to {@code 0}) must be the sequential run's outcomes, and the merged counts and summaries must
 * equal the sequential ones.
 *
 * <p>One {@link JmcModelChecker#check} runs the whole split run; every transition happens inside
 * the strategy's {@code initIteration} ({@link MustStrategy.SplitControl}. The stale-thread wait of
 * {@link MpThread} is raised to 300 s for the run and restored afterwards.
 */
public final class ItemDriver {

    /** The stale-thread wait used in split mode. */
    public static final long SPLIT_STALE_WAIT_MILLIS = 300_000L;

    /** The order in which items are explored. */
    public enum Order {
        /** {@code N − 1} down to {@code 0}: the order the sequential run pops them. */
        SEQUENTIAL,
        /** {@code 0} up to {@code N − 1}. */
        REVERSED
    }

    /**
     * What to run.
     *
     * @param programIdentity the program and configuration identity for the fingerprint (class,
     *     arguments, model)
     * @param program main's body, run with {@link MpProcess#runMain}
     * @param roles the declared roles for export, or {@code null} to count only
     * @param visibleRoles the visible roles for export, or {@code null} to count only
     * @param k the split threshold, at least 1
     * @param order the item order
     * @param onlyItem if present, only this item is explored (isolated-item mode)
     * @param timeoutSeconds the checker's timeout
     */
    public record Config(String programIdentity, Runnable program, List<String> roles, List<String> visibleRoles,
                         int k, Order order, OptionalInt onlyItem, long timeoutSeconds) {

        /** Checks the arguments. */
        public Config {
            Objects.requireNonNull(programIdentity);
            Objects.requireNonNull(program);
            Objects.requireNonNull(order);
            Objects.requireNonNull(onlyItem);
            if (k < 1) {
                throw new IllegalArgumentException("K must be at least 1, was " + k);
            }
            if ((roles == null) != (visibleRoles == null)) {
                throw new IllegalArgumentException("roles and visible roles are given together or not at all");
            }
        }
    }

    /**
     * The result of a split run.
     *
     * @param split whether the root reached the boundary (otherwise the run is the sequential run)
     * @param r the iterations the root finished ({@code R})
     * @param n the stack entries at the boundary ({@code N}); 0 without a split
     * @param fingerprint the replay-state fingerprint; {@code null} without a split
     * @param root the root's segment
     * @param items the items' segments, in the order they were explored
     * @param distinctComplete distinct complete graphs over root and items (union of digests)
     * @param distinctBlocked distinct blocked graphs, likewise
     * @param ran iterations over root and items
     * @param blockedIterations blocked iterations over root and items
     * @param summaries merged summaries (root, then items {@code N − 1} … {@code 0}, first occurrence
     *     kept, multiplicities summed); {@code null} without export
     * @param failure the run's failure message, or {@code null} if it completed
     */
    public record Result(boolean split, int r, int n, String fingerprint, ItemResult root, List<ItemResult> items,
                         int distinctComplete, int distinctBlocked, int ran, int blockedIterations,
                         Map<Summary, Integer> summaries, String failure) {}

    private ItemDriver() {}

    /**
     * Runs the split run.
     *
     * @param config what to run
     * @return the result; a failed run has {@link Result#failure()} set and the segments finished so far
     */
    public static Result run(Config config) {
        Control control = new Control(config);
        MustStrategy strategy = config.roles() == null
                ? new MustStrategy(1L, "build/test-results/jmc-report")
                : new MustStrategy(1L, "build/test-results/jmc-report", config.roles(), config.visibleRoles());
        strategy.setSplitControl(control);
        String failure = null;
        MpThread.setStaleWaitMillis(SPLIT_STALE_WAIT_MILLIS);
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
            if (!strategy.exhausted()) {
                failure = "the run ended before the exploration was complete";
            }
        } catch (JmcCheckerException e) {
            failure = e.getMessage();
        } catch (RuntimeException | Error e) {
            failure = e.toString();
        } finally {
            MpThread.setStaleWaitMillis(MpThread.DEFAULT_STALE_WAIT_MILLIS);
        }
        return control.result(failure);
    }

    /** The control of an in-JVM split run: collects outcomes and segments, hands out items. */
    private static final class Control implements MustStrategy.SplitControl {
        private final Config config;
        private List<ItemResult.Outcome> outcomes = new ArrayList<>();
        private ItemResult root;
        private final List<ItemResult> items = new ArrayList<>();
        private final Deque<Integer> queue = new ArrayDeque<>();
        private boolean split;
        private int rootIterations;
        private int entries;
        private String fingerprint;

        Control(Config config) {
            this.config = config;
        }

        @Override
        public int splitThreshold() {
            return config.k();
        }

        @Override
        public void outcome(boolean blocked, GraphCanon canon, LongFunction<String> names) {
            String digest = TypedGraphEncoding.sha256(TypedGraphEncoding.outcome(canon, names));
            outcomes.add(new ItemResult.Outcome(blocked, digest));
        }

        @Override
        public boolean atBoundary(MustStrategy.SplitView view) {
            split = true;
            rootIterations = view.finishedIterations();
            entries = view.stackTopFirst().size();
            fingerprint = StackFingerprint.of(config.programIdentity(), config.k(), view, root.outcomes());
            if (config.onlyItem().isPresent()) {
                int j = config.onlyItem().getAsInt();
                if (j < 0 || j >= entries) {
                    throw new IllegalArgumentException("item " + j + " is outside 0.." + (entries - 1));
                }
                queue.add(j);
            } else if (config.order() == Order.SEQUENTIAL) {
                for (int j = entries - 1; j >= 0; j--) {
                    queue.add(j);
                }
            } else {
                for (int j = 0; j < entries; j++) {
                    queue.add(j);
                }
            }
            return true;
        }

        @Override
        public void segmentDone(MustStrategy.Segment s) {
            ItemResult result = new ItemResult(s.index(), s.iterations(), s.blockedIterations(),
                    Collections.unmodifiableList(outcomes),
                    s.summaries() == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(s.summaries())),
                    s.names());
            outcomes = new ArrayList<>();
            if (s.index() < 0) {
                root = result;
            } else {
                items.add(result);
            }
        }

        @Override
        public OptionalInt nextItem() {
            Integer j = queue.poll();
            return j == null ? OptionalInt.empty() : OptionalInt.of(j);
        }

        Result result(String failure) {
            List<ItemResult> segments = new ArrayList<>();
            if (root != null) {
                segments.add(root);
            }
            List<ItemResult> byIndexDescending = new ArrayList<>(items);
            byIndexDescending.sort(Comparator.comparingInt(ItemResult::index).reversed());
            segments.addAll(byIndexDescending);
            Set<String> complete = new HashSet<>();
            Set<String> blocked = new HashSet<>();
            int ran = 0;
            int blockedIterations = 0;
            Map<String, Summary> firstByLine = new LinkedHashMap<>();
            Map<String, Integer> countByLine = new LinkedHashMap<>();
            boolean export = config.roles() != null;
            for (ItemResult s : segments) {
                for (ItemResult.Outcome o : s.outcomes()) {
                    (o.blocked() ? blocked : complete).add(o.digest());
                }
                ran += s.iterations();
                blockedIterations += s.blockedIterations();
                if (export && s.summaries() != null) {
                    for (Map.Entry<Summary, Integer> e : s.summaries().entrySet()) {
                        String line = e.getKey().toJson(0);
                        firstByLine.putIfAbsent(line, e.getKey());
                        countByLine.merge(line, e.getValue(), Integer::sum);
                    }
                }
            }
            Map<Summary, Integer> merged = null;
            if (export) {
                merged = new LinkedHashMap<>();
                for (Map.Entry<String, Summary> e : firstByLine.entrySet()) {
                    merged.put(e.getValue(), countByLine.get(e.getKey()));
                }
                merged = Collections.unmodifiableMap(merged);
            }
            List<ItemResult> explored = Collections.unmodifiableList(new ArrayList<>(items));
            return new Result(split, rootIterations, entries, fingerprint, root, explored, complete.size(),
                    blocked.size(), ran, blockedIterations, merged, failure);
        }
    }
}
