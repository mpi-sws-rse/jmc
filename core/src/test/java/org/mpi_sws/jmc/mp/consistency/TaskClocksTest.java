package org.mpi_sws.jmc.mp.consistency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the four operations of the clock discipline and the lemma they exist for.
 *
 * <p>Pins the bullet "<b>CD and vector clocks — the clock algebra</b>" of plan/state/state.md §5.2
 * in full: <em>emit</em> ({@code vc[i] += 1}, then stamp), <em>deliver</em>
 * ({@code vc_i := vc_i ⊔ VC(m)}), <em>spawn</em> (child's clock := parent's clock) and
 * <em>join</em> (joiner := joiner ⊔ joined) — and the <b>S2 obligation</b> stated there:
 * with this discipline, and only with it, {@code VC(s') < VC(s)} iff {@code s'} is {@code porf}-
 * before {@code s}. That lemma is what the CD row of the §5.2 delivery table and Lemma 5.2.1 are
 * evaluated with, and it is the Must paper's {@code so ≜ [S^cd]; porf; [S^cd]} of Definition 3.6.
 *
 * <p>The tick on emit is the part revision 3.1 omitted (review round 2, M3); the spawn propagation
 * is the part the first implementation omitted (rose-state.md F7(b)). Both have a dedicated
 * {@code …_discriminates…} construction here.
 */
public class TaskClocksTest {

    @Test
    void emit_ticksSenderAndReturnsStamp() {
        TaskClocks.Emission first = TaskClocks.EMPTY.emit(1L);
        assertEquals(1L, first.stamp().get(1L), "emit ticks the sender's own component");
        assertEquals(0L, first.stamp().get(2L));
        assertEquals(
                first.stamp(),
                first.clocks().clockOf(1L),
                "the stamp is the sender's clock after the tick, not before");

        TaskClocks.Emission second = first.clocks().emit(1L);
        assertEquals(2L, second.stamp().get(1L), "a second send from the same task ticks again");
        assertTrue(
                first.stamp().lessThan(second.stamp()),
                "two sends from one task are strictly ordered — the M3 regression guard's premise");

        assertEquals(VectorClock.EMPTY, second.clocks().clockOf(2L), "no other task moved");
        // Immutability: the earlier value is untouched, and so is TaskClocks.EMPTY.
        assertEquals(1L, first.clocks().clockOf(1L).get(1L));
        assertEquals(VectorClock.EMPTY, TaskClocks.EMPTY.clockOf(1L));
    }

    @Test
    void deliver_joinsReceiverWithStamp() {
        TaskClocks.Emission e1 = TaskClocks.EMPTY.emit(1L); // stamp {1:1}
        TaskClocks.Emission e2 = e1.clocks().emit(2L); // stamp {2:1}
        TaskClocks clocks = e2.clocks();

        TaskClocks after = clocks.deliver(2L, e1.stamp());
        assertEquals(1L, after.clockOf(2L).get(1L), "the receiver absorbs the sender's component");
        assertEquals(1L, after.clockOf(2L).get(2L), "the receiver keeps its own component");
        assertEquals(
                clocks.clockOf(2L).join(e1.stamp()),
                after.clockOf(2L),
                "deliver is exactly the component-wise join");
        assertEquals(clocks.clockOf(1L), after.clockOf(1L), "the sender is unaffected");

        // Delivery is idempotent for an already-absorbed stamp.
        assertEquals(after.clockOf(2L), after.deliver(2L, e1.stamp()).clockOf(2L));
        // Immutability of the pre-state.
        assertEquals(0L, clocks.clockOf(2L).get(1L));
    }

    @Test
    void spawn_childStartsWithParentClock() {
        TaskClocks.Emission e1 = TaskClocks.EMPTY.emit(1L); // T1's clock is now {1:1}
        TaskClocks spawned = e1.clocks().spawn(1L, 3L);

        assertEquals(
                e1.clocks().clockOf(1L),
                spawned.clockOf(3L),
                "the child starts at the parent's clock at the spawn");
        assertEquals(e1.clocks().clockOf(1L), spawned.clockOf(1L), "the parent continues unchanged");

        // The child's own component then distinguishes it from the parent.
        TaskClocks.Emission e3 = spawned.emit(3L);
        assertEquals(1L, e3.stamp().get(1L));
        assertEquals(1L, e3.stamp().get(3L));
        assertTrue(
                e1.stamp().lessThan(e3.stamp()),
                "everything the parent did before the spawn is porf-before what the child does");

        assertEquals(VectorClock.EMPTY, TaskClocks.EMPTY.spawn(1L, 3L).clockOf(3L));
    }

    /**
     * Review round 1, m1: {@link TaskClocks} never stores a {@link VectorClock#EMPTY} clock, so
     * "absent" and "empty" are one state and map equality is clock-set equality — the same
     * invariant {@link VectorClock} keeps for its own components. Without it,
     * {@code TaskClocks.EMPTY.spawn(1, 3)} agrees with {@code TaskClocks.EMPTY} on every
     * {@code clockOf} query yet compares unequal, which is a second place state merging silently
     * weakens (the M1 defect class, in the other type §5.3 puts in the state).
     */
    @Test
    void spawn_ofNeverTickedParent_isCanonical() {
        assertEquals(
                TaskClocks.EMPTY,
                TaskClocks.EMPTY.spawn(1L, 3L),
                "spawning from a never-ticked parent stores nothing");
        assertEquals(TaskClocks.EMPTY.hashCode(), TaskClocks.EMPTY.spawn(1L, 3L).hashCode());
        assertTrue(
                TaskClocks.EMPTY.spawn(1L, 3L).asMap().isEmpty(),
                "the key is removed, not stored mapping to EMPTY");

        // The same for join, and for a delivery of an empty stamp.
        assertEquals(TaskClocks.EMPTY, TaskClocks.EMPTY.join(1L, 2L));
        assertEquals(TaskClocks.EMPTY, TaskClocks.EMPTY.deliver(1L, VectorClock.EMPTY));

        // Observably identical, which is why the equality above is the right one.
        assertEquals(VectorClock.EMPTY, TaskClocks.EMPTY.spawn(1L, 3L).clockOf(3L));
        assertEquals(TaskClocks.EMPTY.clockOf(3L), TaskClocks.EMPTY.spawn(1L, 3L).clockOf(3L));

        // Discriminating control: a *ticked* parent does store the child, so the canonicalisation
        // is dropping only genuinely empty clocks and not swallowing spawn propagation.
        TaskClocks.Emission e = TaskClocks.EMPTY.emit(1L);
        TaskClocks spawned = e.clocks().spawn(1L, 3L);
        assertNotEquals(TaskClocks.EMPTY, spawned);
        assertEquals(e.stamp(), spawned.clockOf(3L));
        assertFalse(spawned.asMap().isEmpty());
    }

    @Test
    void join_joinerAbsorbsJoinedClock() {
        TaskClocks.Emission e2 = TaskClocks.EMPTY.emit(2L); // T2: {2:1}
        TaskClocks.Emission e1 = e2.clocks().emit(1L); // T1: {1:1}
        TaskClocks joined = e1.clocks().join(1L, 2L);

        assertEquals(1L, joined.clockOf(1L).get(1L), "the joiner keeps its own component");
        assertEquals(1L, joined.clockOf(1L).get(2L), "the joiner absorbs the joined task's");
        assertEquals(
                e1.clocks().clockOf(1L).join(e1.clocks().clockOf(2L)),
                joined.clockOf(1L),
                "join is exactly the component-wise join of the two tasks' clocks");
        assertEquals(e1.clocks().clockOf(2L), joined.clockOf(2L), "the joined task is unaffected");

        TaskClocks.Emission after = joined.emit(1L);
        assertTrue(
                e2.stamp().lessThan(after.stamp()),
                "a send by the joined task is porf-before a send made after the join");
    }

    /**
     * The S2 lemma of plan/state/state.md §5.2, on a small {@code porf} graph built with all four
     * operations. Five sends, ten unordered pairs; the assertion is that {@code <} holds on exactly
     * the six {@code porf} pairs and on neither direction of the four concurrent pairs.
     *
     * <p>The graph (time downwards):
     *
     * <pre>
     *   T1: s1 = emit(1)                      {1:1}
     *       spawn(1 -&gt; 3)                     T3's clock := {1:1}
     *   T3: s3 = emit(3)                      {1:1, 3:1}
     *   T2: s2 = emit(2)                      {2:1}
     *   T1: deliver(1, VC(s2))                T1's clock := {1:1, 2:1}
     *       s4 = emit(1)                      {1:2, 2:1}
     *   T2: join(2, 3)                        T2's clock := {1:1, 2:1, 3:1}
     *       s5 = emit(2)                      {1:1, 2:2, 3:1}
     * </pre>
     *
     * <p>porf: s1&lt;s3 (spawn), s1&lt;s4 (po), s2&lt;s4 (deliver), s1&lt;s5 and s3&lt;s5 (join),
     * s2&lt;s5 (po). Concurrent: s1‖s2, s2‖s3, s3‖s4, s4‖s5.
     */
    @Test
    void vectorClock_strictlyLessIffPorfBefore() {
        TaskClocks c = TaskClocks.EMPTY;

        TaskClocks.Emission e1 = c.emit(1L);
        c = e1.clocks();
        c = c.spawn(1L, 3L);

        TaskClocks.Emission e3 = c.emit(3L);
        c = e3.clocks();

        TaskClocks.Emission e2 = c.emit(2L);
        c = e2.clocks();

        c = c.deliver(1L, e2.stamp());
        TaskClocks.Emission e4 = c.emit(1L);
        c = e4.clocks();

        c = c.join(2L, 3L);
        TaskClocks.Emission e5 = c.emit(2L);

        VectorClock s1 = e1.stamp();
        VectorClock s2 = e2.stamp();
        VectorClock s3 = e3.stamp();
        VectorClock s4 = e4.stamp();
        VectorClock s5 = e5.stamp();

        // The stamps are what the graph above says they are.
        assertEquals(1L, s1.get(1L));
        assertEquals(1L, s3.get(1L));
        assertEquals(1L, s3.get(3L));
        assertEquals(1L, s2.get(2L));
        assertEquals(2L, s4.get(1L));
        assertEquals(1L, s4.get(2L));
        assertEquals(0L, s4.get(3L));
        assertEquals(1L, s5.get(1L));
        assertEquals(2L, s5.get(2L));
        assertEquals(1L, s5.get(3L));

        // porf holds exactly here.
        assertPorf(s1, s3, "spawn: T1's send before the spawn precedes T3's send");
        assertPorf(s1, s4, "po: T1's two sends");
        assertPorf(s2, s4, "deliver: T1 read T2's message before its second send");
        assertPorf(s1, s5, "spawn then join: T1 -> T3 -> T2");
        assertPorf(s3, s5, "join: T3's send precedes T2's send after the join");
        assertPorf(s2, s5, "po: T2's two sends");

        // And nowhere else.
        assertConcurrent(s1, s2, "T1's first send and T2's send are unrelated");
        assertConcurrent(s2, s3, "T2's send and the child's send are unrelated");
        assertConcurrent(s3, s4, "T3's send never reached T1");
        assertConcurrent(s4, s5, "T1's second send never reached T2");
    }

    /**
     * Discriminating construction for the spawn rule — the rose-state.md F7(b) defect. Rebuilds
     * the first three events of the lemma graph <em>without</em> {@link TaskClocks#spawn}, by hand,
     * and asserts that the child's send then becomes {@code cd}-concurrent with the parent's,
     * which is a missed-behaviour error (state.md §5.2). Without this, the {@code assertPorf}
     * above could be passing for the wrong reason.
     */
    @Test
    void vectorClock_discriminatesOmittedSpawnPropagation() {
        TaskClocks.Emission e1 = TaskClocks.EMPTY.emit(1L);

        // Correct: the child inherits the parent's clock at the spawn.
        TaskClocks.Emission withSpawn = e1.clocks().spawn(1L, 3L).emit(3L);
        assertTrue(e1.stamp().lessThan(withSpawn.stamp()));
        assertFalse(e1.stamp().concurrentWith(withSpawn.stamp()));

        // Deliberately wrong: no spawn propagation, so T3 starts from EMPTY.
        TaskClocks.Emission withoutSpawn = e1.clocks().emit(3L);
        assertEquals(VectorClock.EMPTY.tick(3L), withoutSpawn.stamp());
        assertFalse(
                e1.stamp().lessThan(withoutSpawn.stamp()),
                "without spawn propagation the parent's send no longer dominates the child's");
        assertTrue(
                e1.stamp().concurrentWith(withoutSpawn.stamp()),
                "which makes them cd-concurrent — the F7(b) missed-behaviour error");
    }

    /**
     * Discriminating construction for the emit tick — review round 2, M3. Stamp-and-join without
     * the tick is built by hand and shown to leave a task's two consecutive sends with equal,
     * hence unordered, clocks.
     */
    @Test
    void vectorClock_discriminatesOmittedEmitTick() {
        // Correct: emit ticks, so the two stamps are strictly ordered.
        TaskClocks.Emission a = TaskClocks.EMPTY.emit(1L);
        TaskClocks.Emission b = a.clocks().emit(1L);
        assertTrue(a.stamp().lessThan(b.stamp()));

        // Deliberately wrong: "stamp with the sender's current clock, never advance it" — the
        // revision 3.1 wording. T1 has a non-trivial clock (it read a message from T2 first), and
        // both of its sends are stamped with that same clock.
        TaskClocks.Emission fromT2 = TaskClocks.EMPTY.emit(2L);
        VectorClock t1 = fromT2.clocks().deliver(1L, fromT2.stamp()).clockOf(1L);
        VectorClock noTickFirst = t1;
        VectorClock noTickSecond = t1;
        assertEquals(1L, t1.get(2L), "the no-tick stamp still reflects everything T1 has read");
        assertEquals(noTickFirst, noTickSecond);
        assertFalse(noTickFirst.lessThan(noTickSecond), "equal clocks are not strictly ordered");
        assertFalse(noTickSecond.lessThan(noTickFirst));
        assertFalse(
                noTickFirst.concurrentWith(noTickSecond),
                "they are equal, not concurrent — the CD rule simply has nothing to order them by");
    }

    private static void assertPorf(VectorClock earlier, VectorClock later, String why) {
        assertTrue(earlier.lessThan(later), why);
        assertFalse(later.lessThan(earlier), why + " (and not the other way round)");
        assertFalse(earlier.concurrentWith(later), why + " (and not concurrent)");
    }

    private static void assertConcurrent(VectorClock x, VectorClock y, String why) {
        assertTrue(x.concurrentWith(y), why);
        assertFalse(x.lessThan(y), why);
        assertFalse(y.lessThan(x), why);
    }
}
