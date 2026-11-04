package org.mpi_sws.jmc.strategies.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoiceValue;
import org.mpi_sws.jmc.strategies.tracker.TrackActiveTasksStrategy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * JMC-SPEED-2 tester round 01 item 3: J2's {@code MustStrategy.nextTask} min-scan against 3c43b2d's
 * "first of a TreeSet" on random states. The state is the active set (by {@code markActive}),
 * {@code stableOfRuntime}, {@code runtimeOfStable}, {@code parked} and {@code answers}, set by
 * reflection on a fresh strategy. Derived (old code): the candidates are the stable ids of the
 * active runtime ids that have one, minus the parked ids; the least of them is picked, its runtime
 * id is returned with its pending answer (removed from {@code answers}), or with none. Only states
 * with a candidate, or with no candidate and nothing parked (then {@code null}), are compared: with
 * no candidate and something parked both versions run the same (unchanged) re-issue code.
 */
class J2NextTaskTest {

    /** A choice value that is only compared by identity. */
    static final class V extends SchedulingChoiceValue {
        final int n;

        V(int n) {
            this.n = n;
        }

        @Override
        public JsonElement toJson() {
            return new JsonPrimitive(n);
        }

        @Override
        public String type() {
            return "j2-test";
        }
    }

    static Field field(String name) throws NoSuchFieldException {
        Field f = MustStrategy.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @Test
    @SuppressWarnings("unchecked")
    void minScan_equalsTreeSetFirst_onRandomStates() throws Exception {
        Method markActive = TrackActiveTasksStrategy.class.getDeclaredMethod("markActive", Long.class);
        markActive.setAccessible(true);
        Class<?> parkedClass = Class.forName(MustStrategy.class.getName() + "$Parked");
        Constructor<?> parkedCtor = parkedClass.getDeclaredConstructors()[0];
        parkedCtor.setAccessible(true);
        Random r = new Random(20261103L);
        int compared = 0;
        int withAnswer = 0;
        int parkedSkipped = 0;
        int nulls = 0;
        int lostToParked = 0;
        for (int round = 0; round < 3000; round++) {
            MustStrategy s = new MustStrategy(1L, "build/test-results/speed2-j2");
            Map<Long, Long> stableOfRuntime = (Map<Long, Long>) field("stableOfRuntime").get(s);
            Map<Long, Long> runtimeOfStable = new HashMap<>();
            field("runtimeOfStable").set(s, runtimeOfStable);
            TreeMap<Long, Object> parked = (TreeMap<Long, Object>) field("parked").get(s);
            Map<Long, SchedulingChoiceValue> answers = (Map<Long, SchedulingChoiceValue>) field("answers").get(s);
            int n = 1 + r.nextInt(12);
            List<Long> runtime = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                // runtime ids and stable ids in unrelated orders, some ids large and colliding mod 16
                long rt = r.nextBoolean() ? 1 + r.nextInt(40) : 16L * r.nextInt(8) + r.nextInt(3) + 1;
                runtime.add(rt);
                if (r.nextInt(6) != 0) {
                    long st = r.nextBoolean() ? 1 + r.nextInt(30) : 1000L + r.nextInt(5);
                    stableOfRuntime.put(rt, st);
                    runtimeOfStable.put(st, rt);
                }
                if (r.nextInt(4) != 0) {
                    markActive.invoke(s, rt);
                }
            }
            for (Long st : new ArrayList<>(runtimeOfStable.keySet())) {
                if (r.nextInt(3) == 0) {
                    parked.put(st, parkedCtor.newInstance(null, null, true, null, false));
                }
                if (r.nextInt(2) == 0) {
                    answers.put(st, new V(r.nextInt(100)));
                }
            }
            // stableOfRuntime may name a stable id whose runtimeOfStable entry was overwritten; keep the
            // maps inverse, as Must does
            stableOfRuntime.entrySet().removeIf(e -> !e.getKey().equals(runtimeOfStable.get(e.getValue())));
            // the reference (3c43b2d)
            TreeSet<Long> runnable = new TreeSet<>();
            Method gat = TrackActiveTasksStrategy.class.getDeclaredMethod("getActiveTasks");
            gat.setAccessible(true);
            for (Long rt : (Iterable<Long>) gat.invoke(s)) {
                Long st = stableOfRuntime.get(rt);
                if (st != null) {
                    runnable.add(st);
                }
            }
            TreeSet<Long> beforeRemoval = new TreeSet<>(runnable);
            runnable.removeAll(parked.keySet());
            if (runnable.isEmpty() && !parked.isEmpty()) {
                parkedSkipped++;
                continue;
            }
            Map<Long, SchedulingChoiceValue> answersBefore = new HashMap<>(answers);
            SchedulingChoice<?> c = s.nextTask();
            compared++;
            if (runnable.isEmpty()) {
                assertNull(c, "round " + round + ": nothing runnable, nothing parked");
                nulls++;
                continue;
            }
            long t = runnable.first();
            if (beforeRemoval.headSet(t).stream().anyMatch(parked::containsKey)) {
                lostToParked++;
            }
            assertEquals(runtimeOfStable.get(t), c.getTaskId(), "round " + round + ": the task");
            assertSame(answersBefore.get(t), c.getValue(), "round " + round + ": its answer");
            if (answersBefore.get(t) != null) {
                withAnswer++;
            }
            answersBefore.remove(t);
            assertEquals(answersBefore, answers, "round " + round + ": the answer is consumed");
        }
        System.out.println("NEXTTASK compared " + compared + " (with answer " + withAnswer + ", null " + nulls
                + ", a parked task below the pick " + lostToParked + "), skipped (only parked) " + parkedSkipped);
        assertTrue(compared > 1000 && withAnswer > 100 && lostToParked > 100, "the states are not vacuous");
    }
}
