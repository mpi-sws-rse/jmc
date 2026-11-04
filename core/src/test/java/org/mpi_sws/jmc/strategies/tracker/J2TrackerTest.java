package org.mpi_sws.jmc.strategies.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * JMC-SPEED-2 tester round 01 (plan/jmc-must/log/dev/SPEED2/round-01.prompt.md item 3; J2 as
 * changed after Codex round 01): {@code TrackTasks.updateAndRetainActive}, used by the strategy for
 * an instance of exactly {@code TrackTasks}, and {@code TrackActiveTasksStrategy.updateEvent} against the code of
 * 3c43b2d. The old path is {@code candidates.retainAll(tracker.updateEvent(event))}; {@link
 * RefStrategy} is 3c43b2d's {@code updateEvent}, verbatim, over its own tracker instances. Old and new
 * run on independent tracker instances fed the same events (a stateful tracker is never invoked
 * twice), and after every event the candidate set, its iteration order, the trackers' internal
 * state, {@code getActiveTasks()}'s iteration order, the exception (class and message) and the
 * debug-log text must be equal.
 *
 * <p>Derived: {@code HashSet.retainAll(c)} removes the elements of the receiver that {@code c}
 * does not contain, in the receiver's iteration order, so its result (and the receiver's internal
 * table, hence its later iteration order) depends only on membership in {@code c}: {@code
 * retainAll(snapshot of activeTasks)} and {@code retainAll(activeTasks)} under the lock are the
 * same. The refilled {@code activeTasks} keeps its table capacity across events; a replaced one
 * would be sized by the current set, which changes the iteration order of colliding ids.
 */
class J2TrackerTest {

    // ------------------------------------------------------------------ the reference (3c43b2d)

    /** {@code TrackActiveTasksStrategy.updateEvent} and its state as of 3c43b2d. */
    static final class RefStrategy {
        private static final Logger LOGGER = LogManager.getLogger("J2Ref");
        private final Set<Long> allTasks = new HashSet<>();
        private final Set<Long> activeTasks = new HashSet<>();
        private final Object tasksLock = new Object();
        private final List<Tracker> trackers;

        RefStrategy(List<Tracker> trackers) {
            this.trackers = trackers;
        }

        public void updateEvent(JmcRuntimeEvent event) {
            Set<Long> localActiveTasks;
            synchronized (tasksLock) {
                allTasks.add(event.getTaskId());
                localActiveTasks = new HashSet<>(this.allTasks);
            }
            for (Tracker tracker : trackers) {
                localActiveTasks.retainAll(tracker.updateEvent(event));
            }

            LOGGER.debug("Active tasks: {}", new HashSet<>(localActiveTasks));
            synchronized (tasksLock) {
                this.activeTasks.clear();
                this.activeTasks.addAll(localActiveTasks);
            }
        }

        void clear() {
            synchronized (tasksLock) {
                activeTasks.clear();
                allTasks.clear();
            }
            for (Tracker tracker : trackers) {
                tracker.reset();
            }
        }

        Set<Long> getActiveTasks() {
            synchronized (tasksLock) {
                return new HashSet<>(activeTasks);
            }
        }
    }

    /** The production class, with nothing but a public view of its active set. */
    static final class NewStrategy extends TrackActiveTasksStrategy {
        NewStrategy() {
            super();
        }

        NewStrategy(List<Tracker> trackers) {
            super(trackers);
        }

        @Override
        public SchedulingChoice<?> nextTask() {
            return null;
        }

        Set<Long> active() {
            return getActiveTasks();
        }
    }

    static List<Tracker> defaultTrackers() {
        return List.of(new TrackTasks(), new TrackWaitNotify(), new TrackStaticInit(), new TrackExecutors());
    }

    // ------------------------------------------------------------------ events

    /** A monitor with a fixed hash code. */
    record Mon(int id) {}

    /**
     * An executor used only as an event parameter (TrackExecutors registers and shuts it down). Its
     * thread factory creates no thread (round 05 sweep), so no task can ever run and no thread of it
     * can outlive a test.
     */
    static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> null);

    static JmcRuntimeEvent ev(JmcRuntimeEvent.Type type, long task, Object... kv) {
        Map<String, Object> p = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            p.put((String) kv[i], kv[i + 1]);
        }
        return new JmcRuntimeEvent(type, task, p);
    }

    static JmcRuntimeEvent start(long t) {
        return ev(JmcRuntimeEvent.Type.START_EVENT, t);
    }

    static JmcRuntimeEvent finish(long t) {
        return ev(JmcRuntimeEvent.Type.FINISH_EVENT, t);
    }

    static JmcRuntimeEvent join(long t, long target) {
        return ev(JmcRuntimeEvent.Type.JOIN_REQUEST_EVENT, t, "waitingTask", target);
    }

    /** A step of a scenario: an event, or a reset (null). */
    static final JmcRuntimeEvent RESET = null;

    /** The hand-written scenarios: name and steps. */
    static Map<String, List<JmcRuntimeEvent>> scenarios() {
        Map<String, List<JmcRuntimeEvent>> m = new java.util.LinkedHashMap<>();
        Mon a = new Mon(1);
        Mon b = new Mon(17);
        m.put("start-finish", List.of(start(1), start(2), start(3), finish(2), start(4), finish(1), finish(3), finish(4)));
        m.put("finish-with-waiters", List.of(start(1), start(2), start(3), join(1, 3), join(2, 3),
                ev(JmcRuntimeEvent.Type.READ_EVENT, 3), finish(3), start(5), finish(1), finish(2)));
        m.put("join-completed", List.of(start(1), start(2), finish(2), join(1, 2), start(3), join(3, 2)));
        m.put("join-active", List.of(start(1), start(2), join(1, 2), ev(JmcRuntimeEvent.Type.WRITE_EVENT, 2), finish(2)));
        m.put("join-never-started", List.of(start(1), join(1, 9), start(9), finish(9)));
        m.put("repeated-join", List.of(start(1), start(2), join(1, 2), join(1, 2), join(1, 2), finish(2), join(1, 2)));
        m.put("reset", java.util.Arrays.asList(start(1), start(2), join(1, 2), RESET, start(2), start(7), join(7, 2), finish(2),
                RESET, start(1)));
        m.put("notify-all", List.of(start(1), start(2), start(3), start(4),
                ev(JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT, 1, "instance", a),
                ev(JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT, 1, "instance", a),
                ev(JmcRuntimeEvent.Type.WAIT_EVENT, 1, "object", a),
                ev(JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT, 2, "instance", a),
                ev(JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT, 2, "instance", a),
                ev(JmcRuntimeEvent.Type.WAIT_EVENT, 2, "object", a),
                ev(JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT, 3, "instance", a),
                ev(JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT, 3, "instance", a),
                ev(JmcRuntimeEvent.Type.NOTIFY_ALL_EVENT, 3, "object", a),
                ev(JmcRuntimeEvent.Type.LOCK_RELEASE_EVENT, 3, "instance", a),
                ev(JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT, 4, "instance", a),
                ev(JmcRuntimeEvent.Type.WAKEUP_EVENT, 1, "object", a),
                finish(3), finish(4)));
        m.put("notify-one", List.of(start(1), start(2), start(3),
                ev(JmcRuntimeEvent.Type.WAIT_EVENT, 1, "object", b),
                ev(JmcRuntimeEvent.Type.WAIT_EVENT, 2, "object", b),
                ev(JmcRuntimeEvent.Type.NOTIFY_EVENT, 3, "object", b),
                ev(JmcRuntimeEvent.Type.WAKEUP_EVENT, 2, "object", b),
                ev(JmcRuntimeEvent.Type.WAKEUP_EVENT, 1, "object", b)));
        m.put("static-init", List.of(start(1), start(2), start(3),
                ev(JmcRuntimeEvent.Type.START_STATIC_INIT_EVENT, 2),
                ev(JmcRuntimeEvent.Type.START_STATIC_INIT_EVENT, 2),
                ev(JmcRuntimeEvent.Type.READ_EVENT, 1),
                ev(JmcRuntimeEvent.Type.END_STATIC_INIT_EVENT, 2),
                ev(JmcRuntimeEvent.Type.END_STATIC_INIT_EVENT, 2),
                ev(JmcRuntimeEvent.Type.END_STATIC_INIT_EVENT, 3)));
        m.put("executors", java.util.Arrays.asList(start(1),
                ev(JmcRuntimeEvent.Type.EXECUTOR_SHUTDOWN_EVENT, 1, "action", "register", "executor", EXECUTOR),
                start(2), ev(JmcRuntimeEvent.Type.EXECUTOR_SHUTDOWN_EVENT, 2, "action", "shutdown",
                        "executor", EXECUTOR), finish(2), RESET, start(1)));
        // many tasks, then a few survivors whose ids collide in a small table but not in a large one
        List<JmcRuntimeEvent> grow = new ArrayList<>();
        for (long t = 1; t <= 40; t++) {
            grow.add(start(t));
        }
        for (long t = 1; t <= 40; t++) {
            if (t != 3 && t != 19 && t != 35 && t != 17 && t != 1) {
                grow.add(finish(t));
            }
        }
        grow.add(join(19, 3));
        grow.add(finish(3));
        m.put("grow-then-shrink", grow);
        // Derived for the refill: iteration 1 leaves activeTasks (and allTasks) with a 64-slot table;
        // in iteration 2, allTasks holds 15 ids, so the candidate set has 32 slots and iterates
        // 3, 35 (one slot), 19; refilled into 64 slots that is 3, 19, 35, and getActiveTasks()'s
        // 16-slot copy keeps 3, 19, 35 in one slot in that order. A replaced (16-slot) set would
        // give 3, 35, 19.
        List<JmcRuntimeEvent> twoIterations = new ArrayList<>();
        for (long t = 1; t <= 30; t++) {
            twoIterations.add(start(t));
        }
        twoIterations.add(RESET);
        long[] second = {3, 19, 35, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        for (long t : second) {
            twoIterations.add(start(t));
        }
        for (long t : second) {
            if (t != 3 && t != 19 && t != 35) {
                twoIterations.add(finish(t));
            }
        }
        m.put("refill-keeps-capacity", twoIterations);
        return m;
    }

    /** Random event sequences over sparse ids (ids colliding modulo 16, 32 and 64), with resets. */
    static List<JmcRuntimeEvent> random(long seed, int n) {
        Random r = new Random(seed);
        long[] ids = {1, 2, 3, 4, 5, 17, 18, 19, 33, 34, 35, 49, 65, 66, 81, 97, 113, 129, 1000003, 1L << 40};
        Mon[] mons = {new Mon(1), new Mon(2), new Mon(17)};
        List<JmcRuntimeEvent> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long t = ids[r.nextInt(r.nextBoolean() ? 6 : ids.length)];
            int k = r.nextInt(100);
            Mon o = mons[r.nextInt(mons.length)];
            if (k < 30) {
                out.add(start(t));
            } else if (k < 45) {
                out.add(finish(t));
            } else if (k < 60) {
                out.add(join(t, ids[r.nextInt(ids.length)]));
            } else if (k < 64) {
                out.add(ev(JmcRuntimeEvent.Type.WAIT_EVENT, t, "object", o));
            } else if (k < 67) {
                out.add(ev(JmcRuntimeEvent.Type.NOTIFY_EVENT, t, "object", o));
            } else if (k < 70) {
                out.add(ev(JmcRuntimeEvent.Type.NOTIFY_ALL_EVENT, t, "object", o));
            } else if (k < 72) {
                out.add(ev(JmcRuntimeEvent.Type.WAKEUP_EVENT, t, "object", o));
            } else if (k < 75) {
                out.add(ev(JmcRuntimeEvent.Type.LOCK_ACQUIRE_EVENT, t, "instance", o));
            } else if (k < 78) {
                out.add(ev(JmcRuntimeEvent.Type.LOCK_ACQUIRED_EVENT, t, "instance", o));
            } else if (k < 81) {
                out.add(ev(JmcRuntimeEvent.Type.LOCK_RELEASE_EVENT, t, "instance", o));
            } else if (k < 84) {
                out.add(ev(JmcRuntimeEvent.Type.START_STATIC_INIT_EVENT, t));
            } else if (k < 87) {
                out.add(ev(JmcRuntimeEvent.Type.END_STATIC_INIT_EVENT, t));
            } else if (k < 89) {
                out.add(ev(JmcRuntimeEvent.Type.EXECUTOR_SHUTDOWN_EVENT, t, "action", "register", "executor", EXECUTOR));
            } else if (k < 99) {
                out.add(ev(JmcRuntimeEvent.Type.READ_EVENT, t));
            } else {
                out.add(RESET);
            }
        }
        return out;
    }

    static String threw(Supplier<?> s) {
        try {
            s.get();
            return "none";
        } catch (RuntimeException e) {
            // a NullPointerException's message is "helpful" or null depending on whether the JIT
            // has made the throw site a fast throw, which differs between two calls of the same code
            return e.getClass().getName() + (e instanceof NullPointerException ? "" : ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ TrackTasks alone

    static Object field(Object o, String name) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    static String trackTasksState(TrackTasks t) throws ReflectiveOperationException {
        return "active=" + new ArrayList<>((Set<?>) field(t, "activeTasks")) + " waiting=" + field(t, "waitingTasks")
                + " completed=" + new ArrayList<>((Set<?>) field(t, "completedTasks"));
    }

    /** Old and new on two TrackTasks fed one sequence; returns the number of steps compared. */
    static int compareTrackTasks(String name, List<JmcRuntimeEvent> steps) throws Exception {
        TrackTasks oldT = new TrackTasks();
        TrackTasks newT = new TrackTasks();
        Set<Long> all = new HashSet<>();
        int i = 0;
        for (JmcRuntimeEvent e : steps) {
            i++;
            if (e == RESET) {
                oldT.reset();
                newT.reset();
                all.clear();
                continue;
            }
            all.add(e.getTaskId());
            Set<Long> co = new HashSet<>(all);
            Set<Long> cn = new HashSet<>(all);
            String xo = threw(() -> co.retainAll(oldT.updateEvent(e)));
            String xn = threw(() -> {
                newT.updateAndRetainActive(e, cn);
                return null;
            });
            String at = name + " step " + i + " " + e;
            assertEquals(xo, xn, at + ": exception");
            assertEquals(new ArrayList<>(co), new ArrayList<>(cn), at + ": candidates (in iteration order)");
            assertEquals(trackTasksState(oldT), trackTasksState(newT), at + ": tracker state");
        }
        // later states: one more event on both, by the old path, gives the same runnable set
        assertEquals(oldT.updateEvent(start(424242)), newT.updateEvent(start(424242)), name + ": a later updateEvent");
        return i;
    }

    @Test
    void trackTasks_oldPathAndRetainRunnable_handScenarios() throws Exception {
        int n = 0;
        for (Map.Entry<String, List<JmcRuntimeEvent>> s : scenarios().entrySet()) {
            n += compareTrackTasks(s.getKey(), s.getValue());
        }
        System.out.println("TRACKTASKS hand steps " + n);
    }

    @Test
    void trackTasks_oldPathAndRetainRunnable_random() throws Exception {
        int n = 0;
        for (long seed = 1; seed <= 200; seed++) {
            n += compareTrackTasks("random seed " + seed, random(seed, 300));
        }
        System.out.println("TRACKTASKS random steps " + n);
    }

    // ------------------------------------------------------------------ the strategy

    /** Captures the formatted messages of the named loggers at DEBUG. */
    static final class Capture extends AbstractAppender {
        final List<String> lines = Collections.synchronizedList(new ArrayList<>());

        Capture() {
            super("j2-capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            lines.add(event.getLoggerName() + "|" + event.getLevel() + "|" + event.getMessage().getFormattedMessage());
        }

        static Capture on(Level level, String... names) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            org.apache.logging.log4j.core.config.Configuration cfg = ctx.getConfiguration();
            Capture c = new Capture();
            c.start();
            cfg.addAppender(c);
            for (String n : names) {
                LoggerConfig lc = new LoggerConfig(n, level, false);
                lc.addAppender(c, level, null);
                cfg.addLogger(n, lc);
            }
            ctx.updateLoggers();
            return c;
        }

        void off(String... names) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            for (String n : names) {
                ctx.getConfiguration().removeLogger(n);
            }
            ctx.updateLoggers();
            stop();
        }
    }

    static final String PROD_LOGGER = TrackActiveTasksStrategy.class.getName();

    /** Ref and new strategies fed one sequence; every step's active order and exception, and the log. */
    static void compareStrategies(String name, List<JmcRuntimeEvent> steps, Supplier<List<Tracker>> trackers,
            boolean debug) {
        Capture cap = Capture.on(debug ? Level.DEBUG : Level.INFO, PROD_LOGGER, "J2Ref");
        try {
            RefStrategy ref = new RefStrategy(trackers.get());
            NewStrategy now = new NewStrategy(trackers.get());
            int i = 0;
            for (JmcRuntimeEvent e : steps) {
                i++;
                String at = name + " step " + i + " " + e;
                if (e == RESET) {
                    ref.clear();
                    now.resetIteration(i);
                } else {
                    String xr = threw(() -> {
                        ref.updateEvent(e);
                        return null;
                    });
                    String xn = threw(() -> {
                        now.updateEvent(e);
                        return null;
                    });
                    assertEquals(xr, xn, at + ": exception");
                }
                assertEquals(new ArrayList<>(ref.getActiveTasks()), new ArrayList<>(now.active()),
                        at + ": getActiveTasks() in iteration order");
                for (Long t : ref.getActiveTasks()) {
                    assertTrue(now.isActive(t), at + ": isActive " + t);
                }
            }
            List<String> refLog = new ArrayList<>();
            List<String> newLog = new ArrayList<>();
            synchronized (cap.lines) {
                for (String l : cap.lines) {
                    if (l.startsWith("J2Ref|")) {
                        refLog.add(l.substring("J2Ref|".length()));
                    } else if (l.startsWith(PROD_LOGGER + "|")) {
                        newLog.add(l.substring(PROD_LOGGER.length() + 1));
                    }
                }
            }
            assertEquals(refLog, newLog, name + ": the debug log text");
            if (debug) {
                assertFalse(newLog.isEmpty(), name + ": debug lines were logged");
            } else {
                assertTrue(newLog.isEmpty(), name + ": no debug line when debug is off");
            }
        } finally {
            cap.off(PROD_LOGGER, "J2Ref");
        }
    }

    @Test
    void strategy_defaultTrackers_handScenarios_orderAndDebugLog() {
        for (Map.Entry<String, List<JmcRuntimeEvent>> s : scenarios().entrySet()) {
            compareStrategies(s.getKey(), s.getValue(), J2TrackerTest::defaultTrackers, true);
            compareStrategies(s.getKey(), s.getValue(), J2TrackerTest::defaultTrackers, false);
        }
    }

    @Test
    void strategy_defaultTrackers_random_orderAndDebugLog() {
        for (long seed = 1; seed <= 150; seed++) {
            compareStrategies("random seed " + seed, random(seed, 300), J2TrackerTest::defaultTrackers, seed % 2 == 0);
        }
    }

    /** Must and Trust pass {@code List.of(new TrackTasks())}. */
    @Test
    void strategy_trackTasksOnly_random_orderAndDebugLog() {
        for (Map.Entry<String, List<JmcRuntimeEvent>> s : scenarios().entrySet()) {
            compareStrategies(s.getKey(), s.getValue(), () -> List.of(new TrackTasks()), true);
        }
        for (long seed = 1; seed <= 150; seed++) {
            compareStrategies("random seed " + seed, random(seed, 300), () -> List.of(new TrackTasks()), seed % 2 == 1);
        }
    }


    /** A custom tracker (always retainAll(updateEvent)) after TrackTasks (the specialised path). */
    @Test
    void strategy_customTrackerAfterTrackTasks() {
        Supplier<List<Tracker>> ts = () -> List.of(new TrackTasks(), new Tracker() {
            private final Set<Long> seen = new HashSet<>();

            @Override
            public Set<Long> updateEvent(JmcRuntimeEvent event) {
                seen.add(event.getTaskId());
                Set<Long> s = new HashSet<>(seen);
                s.removeIf(t -> t % 3 == 0);
                return s;
            }

            @Override
            public void reset() {
                seen.clear();
            }
        });
        for (long seed = 1; seed <= 50; seed++) {
            compareStrategies("custom seed " + seed, random(seed, 200), ts, true);
        }
    }

    // ------------------------------------------------------------------ Codex round 01 (M2, M3)

    /** Returns no runnable task, after the base update. */
    static final class EmptyTrackTasks extends TrackTasks {
        @Override
        public Set<Long> updateEvent(JmcRuntimeEvent event) {
            super.updateEvent(event);
            return Set.of();
        }
    }

    /** Throws on every event of task 2, after the base update. */
    static final class ThrowingTrackTasks extends TrackTasks {
        @Override
        public Set<Long> updateEvent(JmcRuntimeEvent event) {
            Set<Long> s = super.updateEvent(event);
            if (event.getTaskId() == 2L) {
                throw new IllegalStateException("override refuses task 2");
            }
            return s;
        }
    }

    /** Records every event it is given and drops task 1 from its answer. */
    static final class RecordingTrackTasks extends TrackTasks {
        final List<String> seen = new ArrayList<>();

        @Override
        public Set<Long> updateEvent(JmcRuntimeEvent event) {
            seen.add(event.getType() + "/" + event.getTaskId());
            Set<Long> s = new HashSet<>(super.updateEvent(event));
            s.remove(1L);
            return s;
        }
    }

    /**
     * A {@code TrackTasks} subclass overriding {@code updateEvent} goes through its override, as at
     * 3c43b2d. Derived: EmptyTrackTasks leaves no active task after any event; ThrowingTrackTasks
     * makes every event of task 2 throw its message; RecordingTrackTasks sees every event once and
     * task 1 is never active.
     */
    @Test
    void strategy_trackTasksSubclassOverridingUpdateEvent() {
        List<JmcRuntimeEvent> steps = new ArrayList<>();
        for (List<JmcRuntimeEvent> l : scenarios().values()) {
            steps.addAll(l);
        }
        compareStrategies("empty-subclass", steps, () -> List.of(new EmptyTrackTasks()), true);
        compareStrategies("throwing-subclass", steps, () -> List.of(new ThrowingTrackTasks()), true);
        compareStrategies("throwing-subclass-defaults", steps,
                () -> List.of(new ThrowingTrackTasks(), new TrackWaitNotify(), new TrackStaticInit(), new TrackExecutors()),
                false);
        // the derived values, on the production class alone
        NewStrategy e = new NewStrategy(List.of(new EmptyTrackTasks()));
        e.updateEvent(start(1));
        e.updateEvent(start(2));
        assertEquals(List.of(), new ArrayList<>(e.active()), "empty override: nothing active");
        NewStrategy t = new NewStrategy(List.of(new ThrowingTrackTasks()));
        t.updateEvent(start(1));
        assertEquals("java.lang.IllegalStateException: override refuses task 2", threw(() -> {
            t.updateEvent(start(2));
            return null;
        }));
        RecordingTrackTasks rRef = new RecordingTrackTasks();
        RecordingTrackTasks rNew = new RecordingTrackTasks();
        RefStrategy ref = new RefStrategy(List.of(rRef));
        NewStrategy now = new NewStrategy(List.of(rNew));
        for (JmcRuntimeEvent ev : random(5, 400)) {
            if (ev == RESET) {
                ref.clear();
                now.resetIteration(0);
                continue;
            }
            threw(() -> {
                ref.updateEvent(ev);
                return null;
            });
            threw(() -> {
                now.updateEvent(ev);
                return null;
            });
            assertEquals(new ArrayList<>(ref.getActiveTasks()), new ArrayList<>(now.active()), ev.toString());
            assertFalse(now.active().contains(1L), "task 1 never active");
        }
        assertEquals(rRef.seen, rNew.seen, "the override saw the same events");
        assertTrue(rNew.seen.size() > 300, "the override was called for every event");
    }

    /** A candidate set whose retainAll first resets the tracker. */
    static final class ResettingSet extends HashSet<Long> {
        final Tracker t;

        ResettingSet(Set<Long> init, Tracker t) {
            super(init);
            this.t = t;
        }

        @Override
        public boolean retainAll(java.util.Collection<?> c) {
            t.reset();
            return super.retainAll(c);
        }
    }

    /**
     * reset() during the intersection. Public path (what every caller but the strategy uses, and the
     * strategy for any other tracker): {@code cand.retainAll(t.updateEvent(e))} intersects with the
     * snapshot taken before the reset, so 1 stays (derived). Through the strategy, a second tracker
     * that resets TrackTasks inside its own updateEvent must give the reference's states. The
     * package-private {@code updateAndRetainActive} is never handed a foreign set by the strategy;
     * what it would do with one is printed (NOTE), not asserted: its contract excludes it.
     */
    @Test
    void resetDuringRetainAll_customCandidateSet() {
        TrackTasks t = new TrackTasks();
        t.updateEvent(start(1));
        ResettingSet cand = new ResettingSet(Set.of(1L, 2L), t);
        cand.retainAll(t.updateEvent(start(2)));
        assertEquals(List.of(1L, 2L), new ArrayList<>(cand), "the snapshot survives the reset");
        Supplier<List<Tracker>> ts = () -> {
            TrackTasks first = new TrackTasks();
            return List.of(first, new Tracker() {
                @Override
                public Set<Long> updateEvent(JmcRuntimeEvent event) {
                    if (event.getType() == JmcRuntimeEvent.Type.JOIN_REQUEST_EVENT) {
                        first.reset();
                    }
                    return new HashSet<>(List.of(event.getTaskId(), 1L, 2L, 3L, 17L, 19L, 33L));
                }

                @Override
                public void reset() {}
            });
        };
        for (long seed = 1; seed <= 40; seed++) {
            compareStrategies("reset-in-second-tracker seed " + seed, random(seed, 200), ts, false);
        }
        TrackTasks d = new TrackTasks();
        d.updateEvent(start(1));
        ResettingSet direct = new ResettingSet(Set.of(1L, 2L), d);
        d.updateAndRetainActive(start(2), direct);
        System.out.println("NOTE updateAndRetainActive with a resetting foreign set retains " + new ArrayList<>(direct)
                + " (the public path retains [1, 2]); the strategy never passes such a set");
    }

    /**
     * The concurrent workloads (rounds 01-05): (A) a synchronized candidate set held by another thread
     * that calls into the tracker: the public path holds no tracker lock during the intersection, so it
     * cannot deadlock; (B) the strategy driven from three threads at once (events, readers, resets),
     * every step returning normally and every worker completing all of its steps; (C, NOTE only,
     * finding F2) the package-private path deadlocks with such a set. All three run in a disposable
     * child JVM ({@link J2DeadlockProbe}), so this JVM never hosts a thread that could block; the
     * child is killed and reaped on every path. Derived: the child exits by itself with status 0 and
     * prints exactly the lines {@code J2PROBE-COUNTS caller=20000 events=<non-RESET entries of
     * random(77, 20000)> readers=20000 resets=500} and {@code J2PROBE-OK deadlocked=true}.
     */
    @Test
    void synchronizedCandidateSet_noDeadlock() throws Exception {
        int expectedEvents = (int) random(77, 20000).stream().filter(e -> e != RESET).count();
        Set<Thread> before = Thread.getAllStackTraces().keySet();
        ProbeRun run = runDeadlockProbe(180);
        System.out.println(run.out);
        assertFalse(run.aliveAtReturn, "probe JVM reaped");
        assertFalse(run.interrupted, "not interrupted");
        assertTrue(run.exitedByItself, "probe JVM exited by itself within the timeout\n" + run.out);
        assertEquals(0, run.exitCode, "probe exit status\n" + run.out);
        List<String> lines = run.out.lines().toList();
        assertTrue(lines.stream().noneMatch(l -> l.startsWith("J2PROBE-FAIL")), "no probe failure\n" + run.out);
        assertTrue(lines.contains("J2PROBE-COUNTS caller=20000 events=" + expectedEvents + " readers=20000 resets=500"),
                "probe counts\n" + run.out);
        assertTrue(lines.contains("J2PROBE-OK deadlocked=true"), "probe success marker\n" + run.out);
        for (Thread x : Thread.getAllStackTraces().keySet()) {
            assertFalse(!before.contains(x) && x.getName().startsWith("j2-"), "no workload thread in this JVM: " + x);
        }
    }

    /** The outcome of one {@link J2DeadlockProbe} child JVM. */
    static final class ProbeRun {
        boolean exitedByItself;
        int exitCode = -1;
        boolean aliveAtReturn;
        boolean interrupted;
        String out = "";
    }

    /**
     * Runs {@link J2DeadlockProbe} in a child JVM and waits at most {@code seconds}. The cleanup is in a
     * finally: a live child is killed ({@code destroyForcibly}) and waited for without a deadline
     * (interrupted waits are retried; the interrupt status is restored afterwards), the log is read and
     * deleted. The child also stops itself after 150 s (its watchdog), should this JVM die first.
     */
    static ProbeRun runDeadlockProbe(int seconds) throws java.io.IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java");
        cmd.add("-cp");
        cmd.add(testRuntimeClasspath());
        cmd.add(J2DeadlockProbe.class.getName());
        java.nio.file.Path log = java.nio.file.Files.createTempFile(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir")), "j2dead-", ".txt");
        ProbeRun run = new ProbeRun();
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (p.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS)) {
                run.exitedByItself = true;
                run.exitCode = p.exitValue();
            }
        } catch (InterruptedException e) {
            run.interrupted = true;
        } finally {
            if (p != null) {
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
                // SIGKILL cannot be ignored: wait without a deadline, retrying interrupted waits, so
                // this method never returns while the child is alive
                while (true) {
                    try {
                        p.waitFor();
                        break;
                    } catch (InterruptedException e) {
                        run.interrupted = true;
                    }
                }
                run.aliveAtReturn = p.isAlive();
            }
            try {
                run.out = java.nio.file.Files.readString(log);
            } catch (java.io.IOException e) {
                run.out = "(log unreadable: " + e + ")";
            }
            try {
                java.nio.file.Files.deleteIfExists(log);
            } catch (java.io.IOException e) {
                // left in java.io.tmpdir; nothing else to do
            }
            if (run.interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return run;
    }

    /**
     * The classpath for a child JVM that runs this package's test classes (round 04, item 4). Union, in
     * order, of: the code-source locations of the classes the probe needs (this test, the probe, the
     * tracker, the runtime event, log4j's API and core); the URLs of every {@link java.net.URLClassLoader}
     * on the chain of this class's loader and of the context loader; and {@code java.class.path}. Under
     * Gradle the test worker is started with the test runtime classpath as {@code java.class.path} (the
     * application loader), so that part alone suffices there; the first two parts cover launchers that
     * load tests through a separate URL-based loader. Limit: a launcher whose loader is neither the
     * application loader nor URL-based, and whose classes have no file code source, is not covered.
     */
    static String testRuntimeClasspath() {
        java.util.LinkedHashSet<String> cp = new java.util.LinkedHashSet<>();
        for (Class<?> c : List.of(J2TrackerTest.class, J2DeadlockProbe.class, TrackTasks.class, JmcRuntimeEvent.class,
                LogManager.class, LoggerContext.class)) {
            java.security.CodeSource cs = c.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null && "file".equals(cs.getLocation().getProtocol())) {
                try {
                    cp.add(java.nio.file.Path.of(cs.getLocation().toURI()).toString());
                } catch (java.net.URISyntaxException | IllegalArgumentException e) {
                    // not a plain file location: skipped
                }
            }
        }
        for (ClassLoader start : new ClassLoader[] {J2TrackerTest.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader()}) {
            for (ClassLoader l = start; l != null; l = l.getParent()) {
                if (l instanceof java.net.URLClassLoader u) {
                    for (java.net.URL url : u.getURLs()) {
                        if ("file".equals(url.getProtocol())) {
                            try {
                                cp.add(java.nio.file.Path.of(url.toURI()).toString());
                            } catch (java.net.URISyntaxException | IllegalArgumentException e) {
                                // skipped
                            }
                        }
                    }
                }
            }
        }
        for (String e : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)) {
            if (!e.isEmpty()) {
                cp.add(e);
            }
        }
        return String.join(java.io.File.pathSeparator, cp);
    }
}
