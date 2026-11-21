package org.mpi_sws.jmc.mp.semantics;

import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The per-model delivery rules, stated operationally: given the pending messages, a destination
 * and the receiving handler's guard, which messages <em>may</em> a receive at that destination
 * read.
 *
 * <p>Each rule is the operational form of the corresponding Must consistency predicate:
 * <ul>
 *   <li>{@code ASYN} — Def. 3.4 (well-formedness only): any pending matching message;</li>
 *   <li>{@code P2P} — Def. 3.5, {@code so ≜ [S^p2p]; po; [S^p2p]}: the earliest <em>matching</em>
 *       pending message from each sender; non-matching earlier messages are skipped and stay
 *       pending;</li>
 *   <li>{@code CD} — Def. 3.6, {@code so ≜ [S^cd]; porf; [S^cd]}: the matching pending messages
 *       that are {@code porf}-minimal among matching pending messages, decided by stamps
 *       ({@link Pending#causallyBefore});</li>
 *   <li>{@code MBOX} — Def. 3.7 with the witness total order read as one containing
 *       {@code porf} (the reference implementation's {@code send_before}, TraceForge
 *       {@code cons.rs}): the earliest matching message in global emission order.</li>
 * </ul>
 *
 * <p><b>Why a pending-set rule suffices.</b> For {@code p2p}, {@code cd} and {@code mbox},
 * "earliest matching pending message at the moment of delivery" enforces clauses (b) and (c) of
 * the Must consistency definitions <em>jointly</em>: a send that is pending now but consumed later
 * by a {@code po}-later receive would violate clause (c), and the pending-set rule already
 * excludes it. The guard is applied <em>before</em> the choice, never after: that is the
 * {@code mval} restriction of the Must paper (Section 2.3.3) and the difference from an
 * {@code assume} statement.
 *
 * <p>The {@code ⊥} option of a non-blocking receive is <em>not</em> a delivery rule and does not
 * appear here: it is offered by the backend in addition to whatever this class returns, whether
 * or not anything is pending (Must paper, Section 2.3.2).
 *
 * <p>Results are returned in {@link Pending#CANONICAL} order so that every backend enumerates the
 * same candidates in the same order. This class returns <em>sets</em>; choosing among them is a
 * backend's job.
 */
public final class Delivery {

    private Delivery() {}

    /**
     * The messages a receive at {@code dst} under {@code model} with guard {@code guard} may read
     * from {@code buffers}.
     *
     * <p>The guard must be pure and deterministic: it is evaluated once per pending candidate here,
     * and possibly again by the backend.
     *
     * @param buffers the pending messages
     * @param model the receive's model
     * @param dst the receiving process
     * @param guard the receive's predicate over message values
     * @return the deliverable messages in canonical order; empty if none
     */
    public static List<Pending> deliverable(
            Buffers buffers, CommunicationModel model, Tid dst, Predicate<MpMessage> guard) {
        Objects.requireNonNull(buffers, "buffers");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(dst, "dst");
        Objects.requireNonNull(guard, "guard");
        List<Pending> result = switch (model) {
            case ASYN -> asyn(buffers.pending(model, dst), guard);
            case P2P -> p2p(buffers.pending(model, dst), guard);
            case CD -> cd(buffers.pending(model, dst), guard);
            case MBOX -> mbox(buffers.pending(model, dst), guard);
        };
        result.sort(Pending.CANONICAL);
        return Collections.unmodifiableList(result);
    }

    /**
     * Def. 3.4: every matching pending message.
     *
     * @param pending the messages pending for the destination
     * @param guard the receive's predicate over message values
     * @return a new mutable list of the matching messages
     */
    private static List<Pending> asyn(List<Pending> pending, Predicate<MpMessage> guard) {
        List<Pending> out = new ArrayList<>();
        for (Pending p : pending) {
            if (guard.test(p.envelope().value())) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Def. 3.5: per sender, the earliest matching pending message. {@code pending} is the
     * concatenation of per-sender queues each in emission order ({@link Buffers#pending}), so the
     * first match seen for a sender is that sender's earliest.
     *
     * @param pending the {@code P2P} messages pending for the destination
     * @param guard the receive's predicate over message values
     * @return a new mutable list with at most one message per sender
     */
    private static List<Pending> p2p(List<Pending> pending, Predicate<MpMessage> guard) {
        List<Pending> out = new ArrayList<>();
        TreeSet<Tid> sendersTaken = new TreeSet<>();
        for (Pending p : pending) {
            Tid src = p.envelope().src();
            if (sendersTaken.contains(src)) {
                continue; // an earlier matching message from this sender blocks this one
            }
            if (guard.test(p.envelope().value())) {
                out.add(p);
                sendersTaken.add(src);
            }
            // a non-matching earlier message does not block: it is skipped and stays pending
        }
        return out;
    }

    /**
     * Def. 3.6: among matching pending messages, those not causally dominated by another
     * matching pending message. Non-matching messages impose no constraint ({@code ∩ mval}).
     *
     * @param pending the {@code CD} messages pending for the destination (all stamped)
     * @param guard the receive's predicate over message values
     * @return a new mutable list of the causally minimal matching messages
     */
    private static List<Pending> cd(List<Pending> pending, Predicate<MpMessage> guard) {
        List<Pending> matching = asyn(pending, guard);
        List<Pending> out = new ArrayList<>();
        for (int i = 0; i < matching.size(); i++) {
            Pending m = matching.get(i);
            boolean dominated = false;
            for (int j = 0; j < matching.size(); j++) {
                if (j != i && matching.get(j).causallyBefore(m)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * Def. 3.7 (with a {@code porf}-containing witness order): the earliest matching message in
     * global emission order.
     *
     * @param pending the {@code MBOX} messages pending for the destination, in emission order
     * @param guard the receive's predicate over message values
     * @return a new mutable list with at most one message
     */
    private static List<Pending> mbox(List<Pending> pending, Predicate<MpMessage> guard) {
        for (Pending p : pending) {
            if (guard.test(p.envelope().value())) {
                List<Pending> out = new ArrayList<>(1);
                out.add(p);
                return out;
            }
        }
        return new ArrayList<>();
    }
}
