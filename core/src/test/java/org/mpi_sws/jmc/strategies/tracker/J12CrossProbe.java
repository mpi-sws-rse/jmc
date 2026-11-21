package org.mpi_sws.jmc.strategies.tracker;

import org.apache.logging.log4j.Level;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.RandomSchedulingStrategy;
import org.mpi_sws.jmc.strategies.must.MustStrategy;
import org.mpi_sws.jmc.strategies.trust.TrustStrategy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMC-SPEED-2 tester round 01: the before-versus-after probe for J1 and J2. It uses only API that
 * exists both at 3c43b2d and in this tree (no package-private J2 method), so the same class file runs
 * on both builds; the caller diffs the two outputs. It prints, line by line:
 * <ul>
 *   <li>{@code ACTIVE}: {@code getActiveTasks()} in iteration order (and any exception) after every
 *       step of {@link J2TrackerTest}'s scenarios and random sequences, with the default four
 *       trackers and with {@code TrackTasks} alone, debug on;</li>
 *   <li>{@code LOG}: the strategy's debug-log text for those runs;</li>
 *   <li>{@code NEXT}: Must's {@code nextTask} choice on random states;</li>
 *   <li>{@code SCHED}: the schedules of seeded Random and Trust runs on core programs;</li>
 *   <li>{@code MUST}: graph counts and accessor, constructor and guard call counts of a Must run
 *       whose guarded receives test payload records that carry Tids (onStored, mapTids).</li>
 * </ul>
 */
public final class J12CrossProbe {

    private J12CrossProbe() {}

    static final StringBuilder OUT = new StringBuilder();

    static void out(String s) {
        OUT.append(s).append('\n');
    }

    /** The production class with a public view. */
    static final class Strat extends TrackActiveTasksStrategy {
        Strat(List<Tracker> t) {
            super(t);
        }

        Strat() {
            super();
        }

        @Override
        public SchedulingChoice<?> nextTask() {
            return null;
        }

        Set<Long> active() {
            return getActiveTasks();
        }
    }

    static void strategies(boolean defaults, String name, List<JmcRuntimeEvent> steps) {
        Strat s = defaults ? new Strat() : new Strat(List.of(new TrackTasks()));
        int i = 0;
        for (JmcRuntimeEvent e : steps) {
            i++;
            String x = "none";
            if (e == null) {
                s.resetIteration(i);
            } else {
                x = J2TrackerTest.threw(() -> {
                    s.updateEvent(e);
                    return null;
                });
            }
            out("ACTIVE " + (defaults ? "default " : "tracktasks ") + name + " " + i + " " + new ArrayList<>(s.active())
                    + " " + x);
        }
    }

    // ---------------------------------------------------------------- nextTask

    @SuppressWarnings("unchecked")
    static void nextTask() throws Exception {
        Method markActive = TrackActiveTasksStrategy.class.getDeclaredMethod("markActive", Long.class);
        markActive.setAccessible(true);
        Class<?> parkedClass = Class.forName(MustStrategy.class.getName() + "$Parked");
        Constructor<?> parkedCtor = parkedClass.getDeclaredConstructors()[0];
        parkedCtor.setAccessible(true);
        Field fSor = MustStrategy.class.getDeclaredField("stableOfRuntime");
        Field fRos = MustStrategy.class.getDeclaredField("runtimeOfStable");
        Field fParked = MustStrategy.class.getDeclaredField("parked");
        Field fAnswers = MustStrategy.class.getDeclaredField("answers");
        for (Field f : List.of(fSor, fRos, fParked, fAnswers)) {
            f.setAccessible(true);
        }
        Random r = new Random(4242);
        for (int round = 0; round < 2000; round++) {
            MustStrategy s = new MustStrategy(1L, "build/test-results/speed2-cross");
            Map<Long, Long> sor = (Map<Long, Long>) fSor.get(s);
            Map<Long, Long> ros = new HashMap<>();
            fRos.set(s, ros);
            TreeMap<Long, Object> parked = (TreeMap<Long, Object>) fParked.get(s);
            Map<Long, Object> answers = (Map<Long, Object>) fAnswers.get(s);
            int n = 1 + r.nextInt(12);
            for (int i = 0; i < n; i++) {
                long rt = r.nextBoolean() ? 1 + r.nextInt(40) : 16L * r.nextInt(8) + r.nextInt(3) + 1;
                if (r.nextInt(6) != 0) {
                    long st = r.nextBoolean() ? 1 + r.nextInt(30) : 1000L + r.nextInt(5);
                    sor.put(rt, st);
                    ros.put(st, rt);
                }
                if (r.nextInt(4) != 0) {
                    markActive.invoke(s, rt);
                }
            }
            sor.entrySet().removeIf(e -> !e.getKey().equals(ros.get(e.getValue())));
            for (Long st : new ArrayList<>(ros.keySet())) {
                if (r.nextInt(3) == 0) {
                    parked.put(st, parkedCtor.newInstance(null, null, true, null, false));
                }
                if (r.nextInt(2) == 0) {
                    answers.put(st, new org.mpi_sws.jmc.runtime.scheduling.MpFaultValue("v" + r.nextInt(100)));
                }
            }
            boolean runnable = false;
            for (Long rt : s.getActiveTasks()) {
                Long st = sor.get(rt);
                runnable |= st != null && !parked.containsKey(st);
            }
            if (!runnable && !parked.isEmpty()) {
                out("NEXT " + round + " skipped");
                continue;
            }
            SchedulingChoice<?> c = s.nextTask();
            out("NEXT " + round + " " + (c == null ? "null" : c.getTaskId() + " " + (c.getValue() == null ? "-"
                    : c.getValue().toJson())) + " answers-left " + new TreeMap<>(answers).keySet());
        }
    }

    // ---------------------------------------------------------------- seeded Random / Trust

    static final List<String> SCHEDULE = new ArrayList<>();

    static final class RecRandom extends RandomSchedulingStrategy {
        RecRandom(long seed) {
            super(seed, "build/test-results/speed2-cross-random");
        }

        @Override
        public SchedulingChoice<?> nextTask() {
            SchedulingChoice<?> c = super.nextTask();
            SCHEDULE.add(c == null ? "null" : String.valueOf(c.getTaskId()));
            return c;
        }

        @Override
        public void resetIteration(int iteration) {
            SCHEDULE.add("|");
            super.resetIteration(iteration);
        }
    }

    static final class RecTrust extends TrustStrategy {
        RecTrust(long seed, SchedulingPolicy p) {
            super(seed, p, false, "build/test-results/speed2-cross-trust");
        }

        @Override
        public SchedulingChoice<?> nextTask() {
            SchedulingChoice<?> c = super.nextTask();
            SCHEDULE.add(c == null ? "null" : String.valueOf(c.getTaskId()));
            return c;
        }

        @Override
        public void resetIteration(int iteration) {
            SCHEDULE.add("|");
            super.resetIteration(iteration);
        }
    }

    interface Make {
        org.mpi_sws.jmc.strategies.SchedulingStrategy make();
    }

    static void schedule(String name, Make strategy, String type, int iterations, Runnable program) throws Exception {
        SCHEDULE.clear();
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .strategyType(type)
                .strategyConstructor(c -> strategy.make())
                .numIterations(iterations)
                .seed(7L)
                .reportPath("build/test-results/speed2-cross-" + name)
                .build();
        String verdict;
        try {
            JmcModelCheckerReport rep = new JmcModelChecker(config).check(new JmcFunctionalTestTarget(name, program::run));
            verdict = "report " + rep.getErrorMessage();
        } catch (Throwable t) {
            verdict = "threw " + t.getClass().getName();
        }
        String all = String.join(",", SCHEDULE);
        MessageDigest md = MessageDigest.getInstance("MD5");
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest(all.getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", b));
        }
        out("SCHED " + name + " choices=" + SCHEDULE.size() + " md5=" + hex + " " + verdict);
        out("SCHED " + name + " head " + all.substring(0, Math.min(400, all.length())));
    }

    // ---------------------------------------------------------------- Must guards over Tid payloads

    static final AtomicInteger ACC = new AtomicInteger();
    static final AtomicInteger CTOR = new AtomicInteger();
    static final AtomicInteger GUARD = new AtomicInteger();

    /** A payload with Tids inside a record, a list and an optional. */
    public record Pay(Tid from, int n, List<Tid> boss, java.util.Optional<Inner> in) implements MpMessage {
        public Pay {
            CTOR.incrementAndGet();
        }

        public Tid from() {
            ACC.incrementAndGet();
            return from;
        }

        public int n() {
            ACC.incrementAndGet();
            return n;
        }

        public List<Tid> boss() {
            ACC.incrementAndGet();
            return boss;
        }

        public java.util.Optional<Inner> in() {
            ACC.incrementAndGet();
            return in;
        }
    }

    /** The inner record of {@link Pay}. */
    public record Inner(Tid t) {
        public Inner {
            CTOR.incrementAndGet();
        }

        public Tid t() {
            ACC.incrementAndGet();
            return t;
        }
    }

    static void mustGuards() throws Exception {
        CommunicationModel p2p = CommunicationModel.P2P;
        Runnable body = () -> {
            Tid main = MpProcess.self();
            MpProcess.Handle b = MpProcess.spawn("b", () -> {
                Channel.recv(p2p, m -> {
                    GUARD.incrementAndGet();
                    return m instanceof Pay p && p.n() % 2 == 1;
                });
                Channel.recv(p2p, m -> {
                    GUARD.incrementAndGet();
                    return m instanceof Pay p && p.boss().contains(main);
                });
            });
            Tid bt = b.tid();
            List<MpProcess.Handle> hs = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                int n = i;
                hs.add(MpProcess.spawn("a" + i, () -> Channel.send(p2p, bt,
                        new Pay(MpProcess.self(), n, List.of(main, bt), java.util.Optional.of(new Inner(bt))))));
            }
            for (MpProcess.Handle h : hs) {
                h.await();
            }
            b.await();
        };
        ACC.set(0);
        CTOR.set(0);
        GUARD.set(0);
        MustStrategy s = new MustStrategy(1L, "build/test-results/speed2-cross-must");
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .strategyType("must")
                .strategyConstructor(c -> s)
                .numIterations(100000)
                .seed(1L)
                .reportPath("build/test-results/speed2-cross-must")
                .build();
        String verdict;
        try {
            JmcModelCheckerReport rep = new JmcModelChecker(config).check(new JmcFunctionalTestTarget("guards", body::run));
            verdict = "report " + rep.getErrorMessage();
        } catch (Throwable t) {
            verdict = "threw " + t;
        }
        out("MUST guards complete=" + s.distinctCompleteGraphs() + " blocked=" + s.distinctBlockedGraphs()
                + " iterations=" + s.iterations() + " exhausted=" + s.exhausted() + " accessorCalls=" + ACC.get()
                + " ctorCalls=" + CTOR.get() + " guardCalls=" + GUARD.get() + " " + verdict);
    }

    // ---------------------------------------------------------------- main

    public static void main(String[] args) throws Exception {
        J2TrackerTest.Capture cap = J2TrackerTest.Capture.on(Level.DEBUG, J2TrackerTest.PROD_LOGGER);
        for (Map.Entry<String, List<JmcRuntimeEvent>> e : J2TrackerTest.scenarios().entrySet()) {
            strategies(true, e.getKey(), e.getValue());
            strategies(false, e.getKey(), e.getValue());
        }
        for (long seed = 1; seed <= 60; seed++) {
            strategies(true, "random-" + seed, J2TrackerTest.random(seed, 300));
            strategies(false, "random-" + seed, J2TrackerTest.random(seed, 300));
        }
        synchronized (cap.lines) {
            for (String l : cap.lines) {
                out("LOG " + l);
            }
        }
        cap.off(J2TrackerTest.PROD_LOGGER);
        nextTask();
        Runnable counter = () -> org.mpi_sws.jmc.programs.correct.counter.CorrectCounter.main(new String[0]);
        Runnable atomic = () -> org.mpi_sws.jmc.programs.atomic.counter.AtomicCounter.main(new String[0]);
        schedule("random-correct-counter", () -> new RecRandom(7L), "random", 30, counter);
        schedule("random-atomic-counter", () -> new RecRandom(11L), "random", 30, atomic);
        schedule("trust-random-correct-counter", () -> new RecTrust(7L, TrustStrategy.SchedulingPolicy.RANDOM), "trust",
                30, counter);
        schedule("trust-fifo-atomic-counter", () -> new RecTrust(7L, TrustStrategy.SchedulingPolicy.FIFO), "trust", 30,
                atomic);
        mustGuards();
        System.out.print(OUT);
        System.out.println("DONE");
        System.exit(0);
    }
}
