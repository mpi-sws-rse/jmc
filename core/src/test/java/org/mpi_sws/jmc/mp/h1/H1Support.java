package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared encodings of the H1 ports (plan/jmc-must/log/H1.plan.md §2).
 *
 * <p><b>{@code assume}.</b> TraceForge's {@code assume!(false)} appends a {@code Block(Assume)}
 * label and suspends the thread; the execution is counted blocked once every thread is done or
 * suspended. {@code api.mp} has no assume, so {@link #assume} encodes a false condition as a
 * blocking receive of {@link Never}, a type no program sends: Must parks it, and when nothing else
 * can run gives the task a {@code Block} label — the same end state, reached by the same rule (the
 * other threads run on). This is an encoding of the program, not a JMC feature; it is the one place
 * the ports differ in form from the Rust code.
 *
 * <p><b>Spawning.</b> Rust's {@code thread::spawn} is construct-and-start at the call site; a
 * {@link Spawner} does the same and remembers every thread so that main can await them all at its
 * end (JMC requires it; TraceForge does not, and a join adds no event to a JMC graph). One
 * {@code Spawner} per iteration: create it in {@code run()}.
 */
final class H1Support {

    private H1Support() {}

    /** A message type no H1 program sends; the target of an encoded {@code assume!(false)}. */
    record Never() implements MpMessage {}

    /**
     * TraceForge's {@code assume!(cond)}.
     *
     * @param cond the condition
     * @param model the program's model (Must refuses any other than p2p and asyn)
     */
    static void assume(boolean cond, CommunicationModel model) {
        if (!cond) {
            Channel.recv(model, Never.class);
            throw new IllegalStateException("an encoded assume(false) received a message");
        }
    }

    /** Construct-and-start spawning with a registry for main's final await. */
    static final class Spawner {
        private final List<MpThread> all = new ArrayList<>();

        /**
         * Spawns and starts {@code body}, as Rust's {@code thread::spawn}.
         *
         * @param body the thread body
         * @return the thread's id
         */
        Tid spawn(Runnable body) {
            MpThread t = new MpThread(body);
            synchronized (all) {
                all.add(t);
            }
            t.start();
            return Tid.of(t.getTaskId());
        }

        /**
         * Creates (mints the id of) a thread without starting it, for a Rust program that sends to
         * a thread before spawning it ({@code channel::Builder} channels created before the
         * receiver's spawn): the send names the id, {@link MpThread#start()} comes at the spawn.
         *
         * @param body the thread body
         * @return the unstarted thread, registered for {@link #awaitAll()}
         */
        MpThread create(Runnable body) {
            MpThread t = new MpThread(body);
            synchronized (all) {
                all.add(t);
            }
            return t;
        }

        /** Awaits every thread spawned so far, including ones spawned while awaiting. */
        void awaitAll() {
            for (int i = 0; ; i++) {
                MpThread t;
                synchronized (all) {
                    if (i >= all.size()) {
                        return;
                    }
                    t = all.get(i);
                }
                try {
                    t.join1();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    /**
     * A thread whose body records normal completion, for {@link #joinTf}.
     */
    static final class Joinable {
        final MpThread thread;
        final Tid tid;
        private volatile boolean finished;

        Joinable(Runnable body) {
            this.thread = new MpThread(() -> {
                body.run();
                finished = true;
            });
            this.tid = Tid.of(thread.getTaskId());
        }

        /** Starts the thread (Rust's spawn point). */
        Joinable start() {
            thread.start();
            return this;
        }
    }

    /**
     * TraceForge's {@code JoinHandle::join} (H1 plan §0, the "TF-join encoding"): in TraceForge a
     * join of a thread that never finishes blocks the joiner; JMC's {@link MpThread#join1()}
     * returns once the strategy has blocked the joined task, and the joiner runs on (H1-F4). The
     * encoding blocks the joiner, by an encoded {@code assume!(false)}, when the joined body did
     * not complete.
     *
     * @param j the joined thread
     * @param model the program's model
     */
    static void joinTf(Joinable j, CommunicationModel model) {
        try {
            j.thread.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        if (!j.finished) {
            assume(false, model);
        }
    }

    /**
     * Parses the model argument of a port: {@code fifo} (TraceForge {@code ConsType::FIFO},
     * JMC p2p) or {@code bag} ({@code ConsType::Bag}, JMC asyn).
     *
     * @param s the argument
     * @return the model
     */
    static CommunicationModel model(String s) {
        switch (s.trim()) {
            case "fifo":
                return CommunicationModel.P2P;
            case "bag":
                return CommunicationModel.ASYN;
            default:
                throw new IllegalArgumentException("model must be fifo or bag, was " + s);
        }
    }
}
