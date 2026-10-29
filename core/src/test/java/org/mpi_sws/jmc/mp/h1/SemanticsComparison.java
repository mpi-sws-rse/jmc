package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * TraceForge {@code tests/semantics_comparison.rs}, {@code program1} .. {@code program4}, ported
 * line by line. Every receive is on the thread's own mailbox and unguarded, as in Rust (a typed
 * {@code recv_msg_block::<T>()} does not filter by type; it panics on a wrong type, here a
 * {@code ClassCastException}). Main communicates (it distributes thread ids and acknowledges).
 *
 * <p>Arguments: {@code <program>,<model>} with program {@code p1|p2|p3|p4} and model
 * {@code fifo|bag} (Causal and Mailbox are out of Must's J2 scope).
 */
public final class SemanticsComparison implements Runnable {

    /** An {@code i32} payload. */
    record I(int v) implements MpMessage {}

    /** A {@code ThreadId} payload. */
    record T(Tid t) implements MpMessage {}

    private final String program;
    private final CommunicationModel m;

    /**
     * @param args program and model
     */
    public SemanticsComparison(String... args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("SemanticsComparison takes <p1|p2|p3|p4>,<fifo|bag>");
        }
        this.program = args[0].trim();
        this.m = H1Support.model(args[1]);
    }

    private Tid rt() {
        return ((T) Channel.recv(m)).t();
    }

    private int ri() {
        return ((I) Channel.recv(m)).v();
    }

    private void s(Tid to, int v) {
        Channel.send(m, to, new I(v));
    }

    private void s(Tid to, Tid v) {
        Channel.send(m, to, new T(v));
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        switch (program) {
            case "p1" -> program12(sp, true);
            case "p2" -> program12(sp, false);
            case "p3" -> program3(sp);
            case "p4" -> program4(sp);
            default -> throw new IllegalArgumentException("unknown program " + program);
        }
        sp.awaitAll();
    }

    private void program12(H1Support.Spawner sp, boolean aSendsToB) {
        Tid me = MpProcess.self();
        Tid actorM = sp.spawn(() -> {
            Tid tmain = rt();
            s(tmain, 0);
            ri();
            ri();
            ri();
        });
        Tid actorA = sp.spawn(() -> {
            Tid tmain = rt();
            s(tmain, 0);
            Tid mm = rt();
            s(tmain, 0);
            Tid b = rt();
            s(tmain, 0);
            ri();
            s(mm, 3);
            if (aSendsToB) {
                s(b, 5);
            }
        });
        Tid actorB = sp.spawn(() -> {
            Tid tmain = rt();
            s(tmain, 0);
            Tid mm = rt();
            s(tmain, 0);
            rt();
            s(tmain, 0);
            if (aSendsToB) {
                ri();
            }
            s(mm, 4);
        });
        s(actorM, me);
        ri();
        s(actorA, me);
        ri();
        s(actorA, actorM);
        ri();
        s(actorA, actorB);
        ri();
        s(actorB, me);
        ri();
        s(actorB, actorM);
        ri();
        s(actorB, actorA);
        ri();
        s(actorA, 0);
        s(actorM, 1);
        s(actorM, 2);
    }

    private void program3(H1Support.Spawner sp) {
        Tid a = sp.spawn(() -> {
            ri();
            ri();
            ri();
        });
        s(a, 1);
        s(a, 2);
        s(a, 3);
    }

    private void program4(H1Support.Spawner sp) {
        Tid me = MpProcess.self();
        Tid actorM = sp.spawn(() -> {
            Tid tmain = rt();
            s(tmain, 0);
            ri();
            ri();
        });
        Tid actorA = sp.spawn(() -> {
            Tid tmain = rt();
            s(tmain, 0);
            rt();
            s(tmain, 0);
            rt();
            s(tmain, 0);
            ri();
            ri();
        });
        Tid actorB = sp.spawn(() -> {
            Tid tmain = rt();
            s(tmain, 0);
            Tid mm = rt();
            s(tmain, 0);
            Tid a = rt();
            s(tmain, 0);
            s(a, 3);
            s(mm, 4);
        });
        s(actorM, me);
        ri();
        s(actorA, me);
        ri();
        s(actorA, actorM);
        ri();
        s(actorA, actorB);
        ri();
        s(actorB, me);
        ri();
        s(actorB, actorM);
        ri();
        s(actorB, actorA);
        ri();
        s(actorM, 1);
        s(actorA, 2);
    }
}
