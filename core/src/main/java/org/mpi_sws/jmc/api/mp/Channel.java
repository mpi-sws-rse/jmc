package org.mpi_sws.jmc.api.mp;

import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.JmcUnsupportedEventException;
import org.mpi_sws.jmc.runtime.scheduling.MpDeliveryValue;
import org.mpi_sws.jmc.runtime.scheduling.MpDeliveryValueAdapter;
import org.mpi_sws.jmc.runtime.scheduling.MpFaultValue;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@code send} and {@code recv} (Must paper §2.1): {@code send^M(t, v)} and the two flavours of
 * {@code recv^{M,β}(λx : e)} — blocking ({@code β = b}, {@link #recv}) and non-blocking
 * ({@code β = nb}, {@link #tryRecv}).
 *
 * <p>Each operation is one event reported to the runtime. A send reports
 * {@code MP_SEND_EVENT} and yields — it never blocks and needs no answer, but it is a scheduling
 * point. A receive reports {@code MP_RECV_REQUEST_EVENT} carrying its model, predicate and
 * blocking attribute, yields, and receives the strategy's answer through the yield: an
 * {@link MpDeliveryValue} naming the send read (or {@code ⊥} for a non-blocking receive), or — for
 * a blocking receive the strategy decides to block — a {@code HaltTaskException} that ends the task
 * for this iteration (Must's {@code B} label).
 *
 * <p><b>The predicate is evaluated by the strategy, not here.</b> Filtering candidates in this
 * class would collapse the choice to a single candidate, but which send a receive reads is the
 * strategy's choice. The predicate must be pure and deterministic: the strategy may evaluate it
 * many times, against many candidates, and again in a re-execution. It is evaluated
 * <em>inside the strategy</em>, on the thread of the requester or of a later sender, so it must
 * read only its argument and captured locals or final fields: a guard that touches instrumented
 * shared state or calls any JMC API re-enters the runtime from inside the strategy, which may
 * detect it and fail the run. The safe idioms are record accessors on the message and constants
 * captured at the receive site.
 *
 * <p><b>A guard depends only on the message and its captured values, and never calls the
 * runtime</b> (not even {@link MpProcess#self()}; capture the id at the receive site instead).
 * Under the {@code must} strategy a guard may run on the scheduler's own thread, where a runtime
 * call is not detected: it kills the scheduler and the check hangs.
 *
 * <p><b>What a receive throws is JMC control flow.</b> A {@code HaltTaskException} (this task is
 * blocked for the iteration) or a {@code HaltExecutionException} (the strategy detected a fault)
 * thrown by {@link #recv} or {@link #tryRecv} must propagate: catching it — a
 * {@code catch (RuntimeException e)} around a receive — turns a blocked task into one that runs on,
 * and a detected fault into one the checker hears about only at the end of the iteration (the
 * strategy re-raises a fault the checker did not record). Catch nothing broader than your own
 * message types around message-passing calls.
 *
 * <p>This class has no static state and no static initializer: all communication state is held by
 * the strategy.
 */
public final class Channel {

    private Channel() {}

    /**
     * {@code send^M(dst, value)}: sends from the current task to {@code dst} under {@code model}.
     * Never blocks.
     *
     * @param model the communication model of this send
     * @param dst the destination process
     * @param value the message (an immutable value; see {@link MpMessage})
     */
    public static void send(CommunicationModel model, Tid dst, MpMessage value) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(dst, "dst");
        Objects.requireNonNull(value, "value");
        MpThread.exitIfStale();
        JmcRuntime.updateEvent(
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.MP_SEND_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("model", model)
                        .param("dst", dst)
                        .param("value", value)
                        .build());
        JmcRuntime.yield();
        MpThread.exitIfStale();
    }

    /**
     * {@code recv^M()}: blocking receive of any message under {@code model}.
     *
     * @param model the model
     * @return the message read
     */
    public static MpMessage recv(CommunicationModel model) {
        return recv(model, m -> true);
    }

    /**
     * {@code recv^M(λx : e)}: blocking selective receive.
     *
     * @param model the model
     * @param guard the predicate {@code λx : e}; pure and deterministic
     * @return the message read
     */
    public static MpMessage recv(CommunicationModel model, Predicate<MpMessage> guard) {
        MpDeliveryValue d = request(model, guard, true);
        if (d.isBottom()) {
            throw new IllegalStateException(
                    "contract violation: the strategy answered ⊥ to a blocking receive");
        }
        return d.value();
    }

    /**
     * Typed convenience: blocking receive of an instance of {@code type}.
     *
     * @param model the model
     * @param type the expected message class
     * @param <T> the message type
     * @return the message read
     */
    public static <T extends MpMessage> T recv(CommunicationModel model, Class<T> type) {
        return type.cast(recv(model, type::isInstance));
    }

    /**
     * Typed convenience: blocking selective receive of an instance of {@code type} satisfying
     * {@code guard}.
     *
     * @param model the model
     * @param type the expected message class
     * @param guard the predicate over instances of {@code type}
     * @param <T> the message type
     * @return the message read
     */
    public static <T extends MpMessage> T recv(
            CommunicationModel model, Class<T> type, Predicate<T> guard) {
        return type.cast(recv(model, m -> type.isInstance(m) && guard.test(type.cast(m))));
    }

    /**
     * {@code recv^{M,nb}()}: non-blocking receive; {@link Optional#empty()} is the {@code ⊥} case,
     * which the strategy may choose whether or not a message is pending (Must §2.3.2).
     *
     * @param model the model
     * @return the message, or empty for {@code ⊥}
     */
    public static Optional<MpMessage> tryRecv(CommunicationModel model) {
        return tryRecv(model, m -> true);
    }

    /**
     * {@code recv^{M,nb}(λx : e)}: non-blocking selective receive.
     *
     * @param model the model
     * @param guard the predicate; pure and deterministic
     * @return the message, or empty for {@code ⊥}
     */
    public static Optional<MpMessage> tryRecv(CommunicationModel model, Predicate<MpMessage> guard) {
        MpDeliveryValue d = request(model, guard, false);
        return d.isBottom() ? Optional.empty() : Optional.of(d.value());
    }

    /**
     * Typed convenience: non-blocking receive of an instance of {@code type}.
     *
     * @param model the model
     * @param type the expected message class
     * @param <T> the message type
     * @return the message, or empty for {@code ⊥}
     */
    public static <T extends MpMessage> Optional<T> tryRecv(CommunicationModel model, Class<T> type) {
        return tryRecv(model, type::isInstance).map(type::cast);
    }

    /**
     * Typed convenience: non-blocking selective receive of an instance of {@code type} satisfying
     * {@code guard}.
     *
     * @param model the model
     * @param type the expected message class
     * @param guard the predicate over instances of {@code type}
     * @param <T> the message type
     * @return the message, or empty for {@code ⊥}
     */
    public static <T extends MpMessage> Optional<T> tryRecv(
            CommunicationModel model, Class<T> type, Predicate<T> guard) {
        return tryRecv(model, m -> type.isInstance(m) && guard.test(type.cast(m))).map(type::cast);
    }

    /**
     * Emits a receive request, yields, and unwraps the strategy's answer; an answer of an
     * unexpected kind fails loudly.
     *
     * @param model the communication model
     * @param guard the receive predicate
     * @param blocking whether the receive is blocking
     * @return the delivery chosen by the strategy, possibly {@code ⊥} for a non-blocking receive
     * @throws HaltExecutionException if the strategy answers with a fault it detected elsewhere
     * @throws JmcUnsupportedEventException if the strategy does not answer with a delivery
     */
    private static MpDeliveryValue request(
            CommunicationModel model, Predicate<MpMessage> guard, boolean blocking) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(guard, "guard");
        MpThread.exitIfStale();
        JmcRuntimeEvent request =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("model", model)
                        .param("guard", guard)
                        .param("blocking", blocking)
                        .build();
        MpThread.exitIfStale();
        Object answer = JmcRuntime.updateEventAndYield(request);
        // A process woken because its iteration was reset gets null here: end it silently .
        MpThread.exitIfStale();
        if (answer instanceof MpFaultValue f) {
            // The strategy detected a fault elsewhere and chose this task to report it.
            throw HaltExecutionException.error(f.message());
        }
        if (!(answer instanceof MpDeliveryValue d)) {
            throw new JmcUnsupportedEventException(
                    JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT,
                    "the configured strategy (no MpDeliveryValue came back; got " + answer + ")");
        }
        if (!d.isBottom() && d.value() instanceof MpDeliveryValueAdapter.ReplayPlaceholder) {
            throw new JmcUnsupportedEventException(
                    JmcRuntimeEvent.Type.MP_RECV_REQUEST_EVENT,
                    "replay (a recorded delivery reached the task unresolved; the replaying strategy "
                            + "must substitute the message from its own buffers)");
        }
        return d;
    }
}
