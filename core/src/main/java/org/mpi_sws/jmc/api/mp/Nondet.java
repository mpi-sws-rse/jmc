package org.mpi_sws.jmc.api.mp;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.JmcUnsupportedEventException;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;

import java.util.List;
import java.util.Objects;

/**
 * {@code nondet(S)} (Must paper §2.1): a finite nondeterministic choice, resolved by the scheduling
 * strategy through an {@code ND_EVENT} — never by a random number generator in this
 * class.
 *
 * <p>Options are a {@link List}, not a set, and the order is significant: Must's revisiting
 * condition (Algorithm 1, line 19) admits a backward revisit past a nondeterministic choice only
 * when it took {@code min(S)}, which needs a total order on the options. The order is
 * the caller's list order, so {@code min(S)} is always index {@code 0}.
 *
 * <p>A one-way choice is not a choice: a singleton list (or {@code chooseIndex(1)}) returns
 * without emitting an event, so it adds no spurious {@code ND} node to any graph.
 *
 * <p>What a choice throws is JMC control flow ({@code HaltExecutionException} — a fault the
 * strategy delivers on this task's next event) and must propagate; see {@link Channel}.
 */
public final class Nondet {

    private Nondet() {}

    /**
     * Chooses one of the options.
     *
     * @param options the options in the order that defines {@code min(S)}; non-empty
     * @param <T> the option type
     * @return the chosen option
     */
    public static <T> T choose(List<T> options) {
        Objects.requireNonNull(options, "options");
        if (options.isEmpty()) {
            throw new IllegalArgumentException("nondet() over an empty set of options");
        }
        return options.get(chooseIndex(options.size()));
    }

    /**
     * Chooses a boolean; {@code min(S)} is {@code true}.
     *
     * @return the chosen boolean
     */
    public static boolean chooseBoolean() {
        return choose(List.of(Boolean.TRUE, Boolean.FALSE));
    }

    /**
     * Chooses an index in {@code [0, bound)}.
     *
     * @param bound the number of options; at least 1
     * @return the chosen index
     */
    public static int chooseIndex(int bound) {
        if (bound < 1) {
            throw new IllegalArgumentException("bound must be >= 1, was " + bound);
        }
        if (bound == 1) {
            return 0;
        }
        MpThread.exitIfStale();
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.ND_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("optionCount", bound)
                        .build();
        MpThread.exitIfStale();
        Object answer = JmcRuntime.updateEventAndYield(event);
        MpThread.exitIfStale();
        if (!(answer instanceof PrimitiveValue pv)) {
            throw new JmcUnsupportedEventException(
                    JmcRuntimeEvent.Type.ND_EVENT,
                    "the configured strategy (no PrimitiveValue came back; got " + answer + ")");
        }
        int chosen = pv.asInteger();
        if (chosen < 0 || chosen >= bound) {
            throw new IllegalStateException(
                    "contract violation: the strategy chose " + chosen + " of " + bound + " options");
        }
        return chosen;
    }
}
