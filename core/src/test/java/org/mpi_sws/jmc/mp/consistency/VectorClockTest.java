package org.mpi_sws.jmc.mp.consistency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the clock algebra's carrier: {@link VectorClock} itself.
 *
 * <p>Pins the bullet "<b>CD and vector clocks — the clock algebra</b>" of plan/state/state.md §5.2,
 * specifically the three operations the discipline is built from — {@code tick} (component-wise
 * increment), {@code ⊔} (component-wise max) and the strict order {@code <} the CD row of the
 * §5.2 delivery table is evaluated with. The {@code porf} correspondence itself is
 * {@code TaskClocksTest.vectorClock_strictlyLessIffPorfBefore}; this class only pins the algebra
 * underneath it.
 *
 * <p>Must paper reference: the causal-delivery send order {@code so ≜ [S^cd]; porf; [S^cd]}
 * (Definition 3.6) is decided by {@link VectorClock#lessThan}.
 */
public class VectorClockTest {

    @Test
    void tick_incrementsOnlyOwnComponent() {
        VectorClock a = VectorClock.EMPTY.tick(1L);
        assertEquals(1L, a.get(1L));
        assertEquals(0L, a.get(2L));
        assertEquals(0L, a.get(99L));

        VectorClock b = a.tick(1L);
        assertEquals(2L, b.get(1L));
        assertEquals(0L, b.get(2L));

        VectorClock c = b.tick(2L);
        assertEquals(2L, c.get(1L), "ticking task 2 must not move task 1's component");
        assertEquals(1L, c.get(2L));

        // Immutability: the earlier values are untouched.
        assertEquals(1L, a.get(1L));
        assertEquals(2L, b.get(1L));
        assertEquals(0L, b.get(2L));
    }

    @Test
    void join_isComponentwiseMax() {
        VectorClock a = VectorClock.EMPTY.tick(1L).tick(1L).tick(3L); // {1:2, 3:1}
        VectorClock b = VectorClock.EMPTY.tick(1L).tick(2L); // {1:1, 2:1}

        VectorClock j = a.join(b);
        assertEquals(2L, j.get(1L), "max(2, 1)");
        assertEquals(1L, j.get(2L), "max(0, 1)");
        assertEquals(1L, j.get(3L), "max(1, 0)");

        assertEquals(j, b.join(a), "join is commutative");
        assertEquals(a, a.join(VectorClock.EMPTY), "EMPTY is the unit");
        assertEquals(a, a.join(a), "join is idempotent");

        // Immutability: the operands are untouched.
        assertEquals(0L, a.get(2L));
        assertEquals(0L, b.get(3L));
    }

    @Test
    void leq_lessThan_concurrentWith_areConsistent() {
        VectorClock zero = VectorClock.EMPTY;
        VectorClock a = zero.tick(1L); // {1:1}
        VectorClock b = a.tick(1L); // {1:2}
        VectorClock c = zero.tick(2L); // {2:1}
        VectorClock d = a.join(c); // {1:1, 2:1}

        VectorClock[] all = {zero, a, b, c, d};
        for (VectorClock x : all) {
            for (VectorClock y : all) {
                boolean xy = x.leq(y);
                boolean yx = y.leq(x);
                assertEquals(
                        xy && !x.equals(y),
                        x.lessThan(y),
                        "lessThan must be leq-and-not-equal for " + x + " vs " + y);
                assertEquals(
                        !xy && !yx,
                        x.concurrentWith(y),
                        "concurrentWith must be mutual incomparability for " + x + " vs " + y);
                assertEquals(
                        xy && yx,
                        x.equals(y),
                        "mutual leq must be exactly equality for " + x + " vs " + y);
                // Trichotomy: exactly one of <, >, ==, || holds.
                int holding =
                        (x.lessThan(y) ? 1 : 0)
                                + (y.lessThan(x) ? 1 : 0)
                                + (x.equals(y) ? 1 : 0)
                                + (x.concurrentWith(y) ? 1 : 0);
                assertEquals(1, holding, "exactly one relation must hold for " + x + " vs " + y);
            }
        }

        assertTrue(zero.lessThan(a));
        assertTrue(a.lessThan(b));
        assertTrue(a.lessThan(d));
        assertTrue(c.lessThan(d));
        assertTrue(a.concurrentWith(c));
        assertTrue(b.concurrentWith(d));
        assertFalse(b.lessThan(d));
        assertFalse(d.lessThan(b));
    }

    @Test
    void equals_ignoresZeroComponents() {
        // A component that has never ticked reads as 0 and is not part of the clock's identity.
        VectorClock viaTick = VectorClock.EMPTY.tick(1L);
        VectorClock viaJoinLeft = VectorClock.EMPTY.join(VectorClock.EMPTY.tick(1L));
        VectorClock viaJoinRight = VectorClock.EMPTY.tick(1L).join(VectorClock.EMPTY);

        assertEquals(viaTick, viaJoinLeft);
        assertEquals(viaTick, viaJoinRight);
        assertEquals(viaTick.hashCode(), viaJoinLeft.hashCode());
        assertEquals(viaTick.hashCode(), viaJoinRight.hashCode());
        assertEquals(0L, viaTick.get(2L), "an absent component reads as 0");
        assertEquals(0L, viaTick.get(Long.MIN_VALUE));

        assertEquals(VectorClock.EMPTY, VectorClock.EMPTY.join(VectorClock.EMPTY));
        assertEquals(VectorClock.EMPTY.hashCode(), VectorClock.EMPTY.join(VectorClock.EMPTY).hashCode());
        assertTrue(VectorClock.EMPTY.leq(viaTick), "EMPTY is below every clock");
        assertFalse(viaTick.leq(VectorClock.EMPTY), "leq treats an absent component as 0");
        assertNotEquals(VectorClock.EMPTY, viaTick);
    }

    /**
     * Discriminating construction for this class. Every assertion above would still pass if
     * {@code lessThan} were "at least one component is smaller"; this one would not. Two clocks
     * that each dominate the other somewhere are concurrent in both directions, which is what makes
     * {@code deliverable_causallyConcurrentMessagesAreBothDeliverable} a real case rather than an
     * artefact.
     */
    @Test
    void lessThan_discriminatesConcurrentFromOrdered() {
        VectorClock x = VectorClock.EMPTY.tick(1L).tick(1L); // {1:2}
        VectorClock y = VectorClock.EMPTY.tick(1L).tick(2L); // {1:1, 2:1}

        // x has a bigger component 1, y has a bigger component 2 — neither dominates.
        assertTrue(x.get(1L) > y.get(1L));
        assertTrue(y.get(2L) > x.get(2L));
        assertFalse(x.lessThan(y));
        assertFalse(y.lessThan(x));
        assertFalse(x.leq(y));
        assertFalse(y.leq(x));
        assertTrue(x.concurrentWith(y));
        assertNotEquals(x, y);

        // And the ordered control, so the "false" answers above are not vacuous.
        VectorClock z = y.tick(1L); // {1:2, 2:1}
        assertTrue(x.lessThan(z));
        assertTrue(y.lessThan(z));
        assertFalse(x.concurrentWith(z));
    }
}
