package org.mpi_sws.jmc.mp.events;

/**
 * The label {@code l ∈ Lab} of a Must execution-graph event (Definition 3.1): a send, a receive,
 * a nondeterministic choice, a block, or an error.
 *
 * <p>Labels are pure values. A receive label records its model and blocking attribute
 * {@code β ∈ {b, nb}} but not its predicate: the predicate is behaviour, not identity, and two
 * graphs are compared on {@code po} and {@code rf}, not on predicate objects
 * (see {@link Recv}).
 *
 * <p><b>Where the receive predicate lives.</b> Must's consistency predicates are stated over
 * {@code mval} ({@code val(s) ∈ vals(r)}), so a graph's consistency check and the revisits of a
 * Must exploration need each receive's predicate. The predicate is carried by the graph's receive
 * <em>event</em> as a non-identity attribute, not by this label; graph identity remains
 * {@code po} and {@code rf}.
 */
public sealed interface MpLabel {

    /**
     * {@code S^M(t, v)}: a send of {@code value} to {@code dst} under {@code model}.
     *
     * @param model the communication model
     * @param dst the destination process
     * @param value the message
     */
    record Send(CommunicationModel model, Tid dst, MpMessage value) implements MpLabel {}

    /**
     * {@code R^{M,β}(vals)}: a receive under {@code model}; {@code blocking} is the attribute
     * {@code β} — {@code true} for {@code b}, {@code false} for {@code nb}. Only a non-blocking
     * receive may read {@code ⊥} (well-formedness, Definition 3.3).
     *
     * @param model the communication model
     * @param blocking whether the receive blocks when nothing is deliverable
     */
    record Recv(CommunicationModel model, boolean blocking) implements MpLabel {}

    /**
     * {@code ND^S(v)}: a nondeterministic choice among {@code optionCount} options, of which the
     * option at {@code chosen} (0-based, in the caller's list order, which is the order
     * {@code min(S)} refers to in Algorithm 1, line 19 of the Must paper) was taken.
     *
     * @param optionCount the size of {@code S}, at least 1
     * @param chosen the index of the value taken
     */
    record Nondet(int optionCount, int chosen) implements MpLabel {
        /** Validates {@code 0 <= chosen < optionCount}. */
        public Nondet {
            if (optionCount < 1 || chosen < 0 || chosen >= optionCount) {
                throw new IllegalArgumentException(
                        "chosen " + chosen + " out of range for " + optionCount + " options");
            }
        }
    }

    /**
     * {@code B}: the process is blocked on a receive with nothing to read (Must paper,
     * Section 2.3 and the discussion of blocking and rescheduling).
     */
    record Block() implements MpLabel {}

    /**
     * {@code error}: an assertion failure or other erroneous event.
     *
     * @param message the failure message
     */
    record Error(String message) implements MpLabel {}
}
