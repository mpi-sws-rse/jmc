package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.Optional;

/**
 * Minimal reproducers of the H1 findings (plan/jmc-must/log/H1.report.md), each with its
 * TraceForge twin in the H1 driver ({@code repro} module). Run with {@code MpLauncher <class>:<name> must 0 1 <rep> -}.
 *
 * <p>Each entry gives TraceForge's count and what JMC did <b>when H1 found the defect</b>, before
 * Track D's J2b fixed F2-F4; since J2b, JMC gives TraceForge's count on all of them (J2b report: the
 * eight H1 rows unequal for F2-F4 are now equal). In particular an await of a blocked process now
 * blocks the awaiter (owner's ruling F4), as TraceForge's join does.
 *
 * <ul>
 *   <li>{@code f2_nested_spawn} (H1-F2): {@code A} spawns {@code G}, which sends to main; main's
 *       non-blocking receive reads ⊥ (then main spawns {@code E}) or {@code G}'s message. Task ids
 *       are minted globally in creation order, so {@code G} is task 4 when {@code E} was spawned
 *       first and task 3 when not: the revisited iteration's {@code G} is not the guide's.
 *       TraceForge: 2 executions. JMC: replay drift.</li>
 *   <li>{@code f3_spawn_edge} (H1-F3): main's non-blocking receive, then main spawns the sender.
 *       The send is porf-after the receive (create→begin); TraceForge: 1. JMC (no spawn edge)
 *       revisits the receive with the send: replay drift.</li>
 *   <li>{@code f3_join_edge} (H1-F3): {@code A}'s non-blocking receive; main joins {@code A},
 *       then sends to {@code A}. TraceForge (end→join edge): 1. JMC: replay drift.</li>
 *   <li>{@code f4_join_blocked} (H1-F4): {@code A} blocks on an empty mailbox; main joins
 *       {@code A} and then asserts false. TraceForge: main's join never returns, 0 complete /
 *       1 blocked. JMC before J2b: the await of a blocked process returned and main asserted;
 *       since F4 the await blocks main, as in TraceForge.</li>
 *   <li>{@code f34_server} (H1-F3 + F4): server {@code C} answers one request; client {@code P}
 *       requests and waits; main joins {@code P}, then sends {@code C} a {@code Stop}.
 *       TraceForge: 1 complete. JMC before J2b: the {@code Stop} revisited {@code C}'s receive
 *       (no join edge), {@code P} was blocked, main's await returned, and the graph was an extra
 *       blocked one; since F3 and F4, 1 complete, as in TraceForge.</li>
 * </ul>
 */
public final class H1Repro implements Runnable {

    /** A payload. */
    record I(int v) implements MpMessage {}

    /** A request naming the client. */
    record Req(Tid from) implements MpMessage {}

    /** The reply. */
    record Ack() implements MpMessage {}

    /** Stop. */
    record Stop() implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;

    /**
     * @param args the reproducer's name
     */
    public H1Repro(String... args) {
        this.name = args[0].trim();
    }

    private static void join(MpThread t) {
        try {
            t.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        Tid me = MpProcess.self();
        switch (name) {
            case "f2_nested_spawn" -> {
                sp.spawn(() -> sp.spawn(() -> Channel.send(M, me, new I(1))));
                Optional<MpMessage> x = Channel.tryRecv(M);
                if (x.isEmpty()) {
                    sp.spawn(() -> { });
                }
            }
            case "f3_spawn_edge" -> {
                Channel.tryRecv(M);
                sp.spawn(() -> Channel.send(M, me, new I(1)));
            }
            case "f3_join_edge" -> {
                MpThread a = new MpThread(() -> Channel.tryRecv(M));
                a.start();
                join(a);
                Channel.send(M, Tid.of(a.getTaskId()), new I(1));
            }
            case "f4_join_blocked" -> {
                MpThread a = new MpThread(() -> Channel.recv(M));
                a.start();
                join(a);
                throw new AssertionError("main ran past the join of a blocked thread");
            }
            case "f34_server" -> {
                MpThread c = new MpThread(() -> {
                    MpMessage x = Channel.recv(M);
                    if (x instanceof Req r) {
                        Channel.send(M, r.from(), new Ack());
                    }
                });
                c.start();
                Tid cid = Tid.of(c.getTaskId());
                MpThread p = new MpThread(() -> {
                    Channel.send(M, cid, new Req(MpProcess.self()));
                    Channel.recv(M);
                });
                p.start();
                join(p);
                Channel.send(M, cid, new Stop());
                join(c);
            }
            default -> throw new IllegalArgumentException("unknown reproducer " + name);
        }
        sp.awaitAll();
    }
}
