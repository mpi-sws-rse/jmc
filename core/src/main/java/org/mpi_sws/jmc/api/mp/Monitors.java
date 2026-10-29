package org.mpi_sws.jmc.api.mp;

import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.function.Predicate;

/**
 * The monitor notification wrappers of the Must paper (§2.3.6): a monitor is an ordinary process
 * that receives {@link Notification}s under {@link CommunicationModel#CD} — required for
 * soundness, because under {@code p2p} notifications from different senders could be delivered in
 * either order even when the monitored sends are causally related (Must Example 2.12).
 *
 * <p>The wrappers are the paper's definitions verbatim:
 * <pre>
 * send_not^M(T', m, Tm) ≜ send^cd(Tm, Send(T, T', m)); send^M(T', m)
 * recv_not^M(λx : e, Tm) ≜ m := recv^M(λx : e); send^cd(Tm, Recv(T, m))
 * </pre>
 * A notification is sent just <em>before</em> a send (sends always execute) and just
 * <em>after</em> a receive (receives may block).
 *
 * <p>A monitor needs no infrastructure: spawn it with {@link MpProcess#spawn}, and have its body
 * {@code recv} notifications under {@code CD} in a loop, asserting on unexpected orderings.
 */
public final class Monitors {

    private Monitors() {}

    /**
     * A notification a monitor observes. Carries the sender in the payload, since a receiver never
     * sees the sender of a message otherwise.
     */
    public sealed interface Notification extends MpMessage permits Notification.Send, Notification.Recv {

        /**
         * {@code from} sent {@code message} to {@code to}.
         *
         * @param from the sender
         * @param to the destination
         * @param message the message sent
         */
        record Send(Tid from, Tid to, MpMessage message) implements Notification {}

        /**
         * {@code at} received {@code message}.
         *
         * @param at the receiver
         * @param message the message received
         */
        record Recv(Tid at, MpMessage message) implements Notification {}
    }

    /**
     * {@code send_not^M(dst, value, monitor)}.
     *
     * @param model the model of the monitored send
     * @param dst the destination
     * @param value the message
     * @param monitor the monitor process
     */
    public static void sendNotified(
            CommunicationModel model, Tid dst, MpMessage value, Tid monitor) {
        Channel.send(CommunicationModel.CD, monitor, new Notification.Send(MpProcess.self(), dst, value));
        Channel.send(model, dst, value);
    }

    /**
     * {@code recv_not^M(λx : e, monitor)}.
     *
     * @param model the model of the monitored receive
     * @param guard the receive predicate
     * @param monitor the monitor process
     * @return the message received
     */
    public static MpMessage recvNotified(
            CommunicationModel model, Predicate<MpMessage> guard, Tid monitor) {
        MpMessage value = Channel.recv(model, guard);
        Channel.send(CommunicationModel.CD, monitor, new Notification.Recv(MpProcess.self(), value));
        return value;
    }
}
