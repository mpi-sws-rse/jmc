package org.mpi_sws.jmc.strategies.must;

import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * P4 tester probe (plan/jmc-must/P4-PROPOSAL.md rev 7, §3 Replay and §4): the Must strategy unchanged, plus
 * <ul>
 *   <li>an event log, one line per event the strategy sees: {@code it=<n> <TYPE> task=<runtime id>} followed by
 *       {@code createdTask=}, {@code startedBy=}, {@code waitingTask=}, {@code joinedTask=}, {@code dst=} where
 *       the event carries them (the format of {@code EventLogStrategy}, plus {@code createdTask});</li>
 *   <li>the stack-item probe of the guide invariant: before every {@link #initIteration} (and at
 *       {@link #teardown}) every exploration-stack entry, and the split snapshot, is read by reflection (the
 *       private {@code stack} and {@code snapshot} fields; each item record's {@code g} component) and every
 *       item whose graph holds a {@link MpLabel.Block} is recorded. Items are only popped inside
 *       {@code initIteration}, so every item ever pushed is seen at least once, except those pushed in an
 *       iteration after which the checker stops on an error (seen at teardown);</li>
 *   <li>each counted iteration's finished graph (its {@code toString} and {@code canon}), in order.</li>
 * </ul>
 * Lives in the strategy's package so that it can read the package-private {@link Graph}. Uses only the API of
 * f3a5de93, so it compiles on the baseline.
 */
public class P4Must extends MustStrategy {

    private static final Field STACK;
    private static final Field SNAPSHOT;

    static {
        try {
            STACK = MustStrategy.class.getDeclaredField("stack");
            STACK.setAccessible(true);
            SNAPSHOT = MustStrategy.class.getDeclaredField("snapshot");
            SNAPSHOT.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    private final List<String> creations = Collections.synchronizedList(new ArrayList<>());
    private final Map<Object, Boolean> seen = new IdentityHashMap<>();
    private final List<String> blockItems = new ArrayList<>();
    private final List<String> graphs = new ArrayList<>();
    private final List<GraphCanon> canons = new ArrayList<>();
    private volatile int iteration;

    /**
     * The counting constructor.
     *
     * @param seed unused
     * @param reportPath unused
     */
    public P4Must(Long seed, String reportPath) {
        super(seed, reportPath);
    }

    /**
     * The exporting constructor.
     *
     * @param seed unused
     * @param reportPath unused
     * @param roles the declared roles
     * @param visible the visible roles
     */
    public P4Must(Long seed, String reportPath, List<String> roles, List<String> visible) {
        super(seed, reportPath, roles, visible);
    }

    @Override
    public void initIteration(int iteration, JmcModelCheckerReport report) throws HaltCheckerException {
        this.iteration = iteration;
        inspect();
        super.initIteration(iteration, report);
    }

    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        StringBuilder sb = new StringBuilder("it=").append(iteration).append(' ').append(event.getType())
                .append(" task=").append(event.getTaskId());
        Map<String, Object> ps = event.getParams();
        for (String k : List.of("createdTask", "startedBy", "waitingTask", "joinedTask")) {
            Object v = ps == null ? null : ps.get(k);
            if (v != null) {
                sb.append(' ').append(k).append('=').append(v);
            }
        }
        Object dst = ps == null ? null : ps.get("dst");
        if (dst instanceof Tid t) {
            sb.append(" dst=").append(t.raw());
        }
        log.add(sb.toString());
        if (event.getType() == JmcRuntimeEvent.Type.TASK_CREATED_EVENT) {
            // Round 03 (Codex code review 01, P3): the creation parameters, kept apart from the log so that the
            // log's format (and the goldens built on it) is unchanged. A missing key prints as <absent>.
            creations.add("it=" + iteration + " createdTask=" + param(ps, "createdTask") + " role=" + param(ps, "role")
                    + " daemon=" + param(ps, "daemon") + " pending=" + param(ps, "pending"));
        }
        super.updateEvent(event);
    }

    @Override
    public void resetIteration(int iteration) {
        int before = iterations();
        try {
            super.resetIteration(iteration);
        } finally {
            if (iterations() > before) {
                ExecutionGraph g = currentGraph();
                graphs.add(g.toString());
                canons.add(g.canon());
            }
        }
    }

    @Override
    public void teardown(JmcModelCheckerReport report) {
        inspect();
        super.teardown(report);
    }

    /** Reads every stack entry and the split snapshot; records the items whose graph holds a Block. */
    private synchronized void inspect() {
        try {
            for (Object item : new ArrayList<>((Collection<?>) STACK.get(this))) {
                consider(item, "stack");
            }
            Object snap = SNAPSHOT.get(this);
            if (snap != null) {
                for (Object item : (List<?>) snap) {
                    consider(item, "snapshot");
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private void consider(Object item, String where) {
        if (seen.put(item, Boolean.TRUE) != null) {
            return;
        }
        Graph g = graphOf(item);
        if (g.hasBlock()) {
            blockItems.add(where + " " + item.getClass().getSimpleName() + " before it=" + iteration + ": " + g);
        }
    }

    /** The {@code g} component of a stack item (Forward, Nondet, Backward). */
    static Graph graphOf(Object item) {
        try {
            for (RecordComponent c : item.getClass().getRecordComponents()) {
                if (c.getName().equals("g")) {
                    Method m = c.getAccessor();
                    m.setAccessible(true);
                    return (Graph) m.invoke(item);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read the graph of " + item, e);
        }
        throw new IllegalStateException("stack item without a graph component: " + item.getClass());
    }

    private static String param(Map<String, Object> ps, String key) {
        return ps != null && ps.containsKey(key) ? String.valueOf(ps.get(key)) : "<absent>";
    }

    /**
     * The parameters of every {@code TASK_CREATED_EVENT}, in order: {@code it=<n> createdTask=<id> role=<r>
     * daemon=<b> pending=<p>}, each value as given or {@code <absent>} when the event lacks the key.
     *
     * @return a copy
     */
    public List<String> creations() {
        synchronized (creations) {
            return new ArrayList<>(creations);
        }
    }

    /** @return the event log so far, a copy */
    public List<String> log() {
        synchronized (log) {
            return new ArrayList<>(log);
        }
    }

    /** @return the number of distinct stack items inspected */
    public synchronized int itemsSeen() {
        return seen.size();
    }

    /** @return descriptions of the inspected stack items whose graph holds a Block */
    public synchronized List<String> blockItems() {
        return new ArrayList<>(blockItems);
    }

    /** @return the counted iterations' graphs, in order */
    public List<String> graphs() {
        return new ArrayList<>(graphs);
    }

    /** @return the counted iterations' canonical forms, in order */
    public List<GraphCanon> canons() {
        return new ArrayList<>(canons);
    }

    /**
     * The entries of a split view whose graph holds a Block.
     *
     * @param view the view at the boundary
     * @return a description per such entry
     */
    public static List<String> blockEntries(SplitView view) {
        List<String> out = new ArrayList<>();
        for (EntryView e : view.stackTopFirst()) {
            for (NodeView n : e.g().nodesByStamp()) {
                if (n.label() instanceof MpLabel.Block) {
                    out.add(e.variant() + " " + e.g().nodesByStamp());
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Process threads still registered alive in {@link MpThread} (its private {@code ALIVE}), for the check that
     * no task is left paused after a run.
     *
     * @return their names
     */
    public static List<String> aliveProcessThreads() {
        try {
            Field f = MpThread.class.getDeclaredField("ALIVE");
            f.setAccessible(true);
            Set<?> alive = (Set<?>) f.get(null);
            synchronized (alive) {
                List<String> out = new ArrayList<>();
                for (Object t : alive) {
                    out.add(t + (t instanceof Thread th && th.isAlive() ? "(alive)" : "(ended)"));
                }
                return out;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Waits up to {@code millis} for {@link #aliveProcessThreads()} to become empty.
     *
     * @param millis the bound
     * @return what is still registered at the end (empty when none)
     * @throws InterruptedException if interrupted
     */
    public static List<String> awaitNoProcessThreads(long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        List<String> a = aliveProcessThreads();
        while (!a.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            a = aliveProcessThreads();
        }
        return a;
    }

    /**
     * An in-process split control: splits at the first iteration boundary with at least {@code k} stack entries,
     * explores every item here in index order (0 first), records the boundary view, the segments and the
     * outcomes' canonical forms (complete and blocked separately).
     */
    public static final class InlineSplit implements SplitControl {
        private final int k;
        private SplitView view;
        private int items = -1;
        private int next;
        private final List<Segment> segments = new ArrayList<>();
        private final Set<GraphCanon> complete = new LinkedHashSet<>();
        private final Set<GraphCanon> blocked = new LinkedHashSet<>();

        /**
         * @param k the split threshold, at least 1
         */
        public InlineSplit(int k) {
            this.k = k;
        }

        @Override
        public int splitThreshold() {
            return k;
        }

        @Override
        public void outcome(boolean isBlocked, GraphCanon canon, LongFunction<String> names) {
            (isBlocked ? blocked : complete).add(canon);
        }

        @Override
        public boolean atBoundary(SplitView v) {
            this.view = v;
            this.items = v.stackTopFirst().size();
            return true;
        }

        @Override
        public void segmentDone(Segment segment) {
            segments.add(segment);
        }

        @Override
        public OptionalInt nextItem() {
            if (items < 0 || next >= items) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(next++);
        }

        /** @return the view at the boundary, or {@code null} if the root never reached it */
        public SplitView view() {
            return view;
        }

        /** @return the closed segments, the root's first */
        public List<Segment> segments() {
            return new ArrayList<>(segments);
        }

        /** @return the item segments (index at least 0) that ran at least one iteration */
        public long itemsThatRan() {
            return segments.stream().filter(s -> s.index() >= 0 && s.iterations() > 0).count();
        }

        /** @return the iterations of all segments */
        public int iterations() {
            return segments.stream().mapToInt(Segment::iterations).sum();
        }

        /** @return the distinct complete graphs over all segments */
        public int distinctComplete() {
            return complete.size();
        }

        /** @return the distinct blocked graphs over all segments */
        public int distinctBlocked() {
            return blocked.size();
        }
    }
}
