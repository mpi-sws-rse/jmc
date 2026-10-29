package org.mpi_sws.jmc.mp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.JmcUnsupportedEventException;
import org.mpi_sws.jmc.strategies.trust.Event;
import org.mpi_sws.jmc.strategies.trust.EventFactory;

import java.util.List;

/**
 * The {@code trust} family's half of L1a — plan/state/mp-contract.md §3.1: every message-passing
 * event has an <em>explicit</em> case in {@code EventFactory.fromRuntimeEvent}, and that case
 * throws.
 *
 * <p>{@code EventFactory} ends with {@code return new ArrayList<>();} after its switch, so an event
 * type with no case is not an error — it is silently translated to nothing, and the Trust algorithm
 * proceeds as if the operation had not happened. That fallthrough is rose-state.md F2, the reason
 * the first implementation of this API could emit an event no strategy modelled and still produce a
 * green run. It is deliberately left in place for the event types that are genuinely irrelevant to
 * Trust (a {@code strategies/trust} change is out of scope under L4), so the guarantee this test
 * pins is narrower and exactly right: the three {@code MP} types are not among them.
 *
 * <p>This is a plain JUnit test in {@code core}: {@code EventFactory} is a pure function from a
 * {@code JmcRuntimeEvent} to a list, so it needs no runtime, no agent and no strategy instance.
 */
public class MpApiEventFactoryTest {

    /**
     * The three event types the message-passing API emits (plan/state/mp-contract.md §2), which the
     * {@code trust} family must refuse rather than drop.
     */
    private static final List<JmcRuntimeEvent.Type> MP_TYPES =
            List.of(
                    JmcRuntimeEvent.Type.MP_SEND_EVENT,
                    JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT,
                    JmcRuntimeEvent.Type.ND_EVENT);

    /**
     * A runtime event type that {@code EventFactory}'s switch does <em>not</em> handle, used as the
     * control. {@code EXECUTOR_SHUTDOWN_EVENT} is an executor-tracking event with no Trust meaning;
     * it takes the empty-list fallthrough, and its behaviour is what a deleted {@code MP} case would
     * degrade to.
     */
    private static final JmcRuntimeEvent.Type UNHANDLED_TYPE =
            JmcRuntimeEvent.Type.EXECUTOR_SHUTDOWN_EVENT;

    /**
     * Each of the three types is refused by name, and the refusal names the type it refused and the
     * family that declined it.
     *
     * <p>The discriminating construction is {@link #UNHANDLED_TYPE}: fed to the same method it
     * returns an empty list without complaint. That is precisely what deleting one of the three
     * cases would produce — a translation to nothing, indistinguishable from an operation that never
     * happened — so the pair pins the distinction the contract cares about rather than merely
     * observing that something was thrown. Asserting the empty list is <em>returned</em> (rather
     * than asserting only that nothing was thrown) also rules out the reading in which the
     * fallthrough had been removed altogether.
     */
    @Test
    public void eventFactory_hasAnExplicitCaseForEveryMpEventType() {
        for (JmcRuntimeEvent.Type type : MP_TYPES) {
            JmcRuntimeEvent event =
                    new JmcRuntimeEvent.Builder().type(type).taskId(1L).build();

            JmcUnsupportedEventException thrown =
                    assertThrows(
                            JmcUnsupportedEventException.class,
                            () -> EventFactory.fromRuntimeEvent(event),
                            type + " must have an explicit case that throws");

            assertEquals(type, thrown.type(), "the refusal must name the event it refused");
            assertTrue(
                    thrown.getMessage().contains(type.name()),
                    "the message must name the type: " + thrown.getMessage());
            assertTrue(
                    thrown.getMessage().contains("trust"),
                    "the message must name the family that declined: " + thrown.getMessage());
        }

        // The control: an unhandled type takes the empty-list fallthrough instead of throwing.
        JmcRuntimeEvent unhandled =
                new JmcRuntimeEvent.Builder().type(UNHANDLED_TYPE).taskId(1L).build();
        List<Event> translated = EventFactory.fromRuntimeEvent(unhandled);
        assertTrue(
                translated.isEmpty(),
                UNHANDLED_TYPE + " is expected to translate to nothing; it translated to "
                        + translated);
    }
}
