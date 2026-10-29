package org.mpi_sws.jmc.mp.programs.j4;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * TraceForge's A3 lock service (conformance `grid.rs` `a3_spec`/`a3_impl`; criteria
 * `plan/traceForge/criteria/P5-APPS.md` "A3 — lock service"), ported to {@code api.mp}.
 *
 * <p>Clients {@code c0..c(k-1)}, {@code q} rounds each of {@code send Acq(i)}, {@code recv Grant},
 * {@code send Rel(i)}. <b>Spec</b>: one invisible {@code lock}: {@code k*q} times
 * {@code recv Acq(i); send Grant(i, round_i); recv Rel} — TraceForge's two channels (acquire,
 * release) become type-test receives, which the summary cannot tell apart since a send observes its
 * value only. <b>Impl</b>: one invisible {@code coordinator} with one mailbox of {@code Msg} and a
 * waiter queue: {@code 2*k*q} unguarded receives; on {@code Acq} grant if free else enqueue; on
 * {@code Rel} grant the head or free the lock. Coordinator kinds as in Rust: {@code FIFO},
 * {@code DOUBLE_GRANT} (grant at once even when held), {@code NEVER_GRANT} ({@code c(k-1)} is never
 * granted from the queue), {@code WRONG_ROUND} (grants to {@code c(k-1)} carry {@code j+1}),
 * {@code LIFO} (the conforming control, the grid row {@code control-lifo}).
 *
 * <p>Spawn order: the server (lock or coordinator) first, then the clients; main is silent. The
 * server needs the clients' identities and the clients the server's: all threads are constructed
 * first ({@link MpThread} mints the task id at construction) and started afterwards in that order,
 * so every body sees every identity. Values: {@code Acq}/{@code Rel} carry the client index,
 * {@code Grant} the index and the round, as the Rust enums do; tags {@code Acq}, {@code Rel},
 * {@code Grant} must match the TraceForge exporter's.
 */
public final class A3Lock implements MpProgram {

    /** The request enum of the Rust fixture: {@code Msg::{Acq(i), Rel(i)}}. */
    public sealed interface Msg extends MpMessage permits Acq, Rel {}

    /** {@code Acq(i)}. */
    @Tag("Acq")
    public record Acq(int i) implements Msg {}

    /** {@code Rel(i)}. */
    @Tag("Rel")
    public record Rel(int i) implements Msg {}

    /** {@code Grant(i, round)}. */
    @Tag("Grant")
    public record Grant(int i, int round) implements MpMessage {}

    /** The rows of TraceForge's grid: {@code SPEC} is the specification, the rest {@code A3Coord}. */
    public enum Kind {
        SPEC("spec"),
        FIFO("fifo"),
        DOUBLE_GRANT("double-grant"),
        NEVER_GRANT("never-grant"),
        WRONG_ROUND("wrong-round"),
        LIFO("control-lifo");

        final String label;

        Kind(String label) {
            this.label = label;
        }

        static Kind byLabel(String label) {
            for (Kind k : values()) {
                if (k.label.equals(label.trim())) {
                    return k;
                }
            }
            throw new IllegalArgumentException("unknown A3 kind " + label + "; one of spec, fifo, double-grant,"
                    + " never-grant, wrong-round, control-lifo");
        }
    }

    private final int clientCount;
    private final int rounds;
    private final Kind kind;

    /** The launcher default: Impl FIFO at {@code (k, q) = (2, 1)}. */
    public A3Lock() {
        this(2, 1, Kind.FIFO);
    }

    /**
     * The launcher's {@code A3Lock:<kind>,<k>,<q>} form.
     *
     * @param args the kind's label (the grid row name), then {@code k} and {@code q}
     */
    public A3Lock(String... args) {
        this(sizeArg(args, 1), sizeArg(args, 2), Kind.byLabel(kindArg(args)));
    }

    /**
     * A row at a size.
     *
     * @param clientCount {@code k}, the number of clients
     * @param rounds {@code q}, the acquire/release rounds per client
     * @param kind the row
     */
    public A3Lock(int clientCount, int rounds, Kind kind) {
        if (clientCount < 1 || rounds < 1) {
            throw new IllegalArgumentException("k and q must be at least 1");
        }
        this.clientCount = clientCount;
        this.rounds = rounds;
        this.kind = kind;
    }

    private static String kindArg(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("A3Lock takes <kind>,<k>,<q>");
        }
        return args[0];
    }

    private static int sizeArg(String[] args, int at) {
        if (args.length != 3) {
            throw new IllegalArgumentException("A3Lock takes <kind>,<k>,<q>");
        }
        return Integer.parseInt(args[at].trim());
    }

    @Override
    public String programName() {
        return "a3-" + kind.label + "-" + size();
    }

    @Override
    public String size() {
        return "k" + clientCount + "q" + rounds;
    }

    @Override
    public CommunicationModel model() {
        return CommunicationModel.P2P;
    }

    @Override
    public List<String> rolesInSpawnOrder() {
        List<String> roles = new ArrayList<>();
        roles.add(kind == Kind.SPEC ? "lock" : "coordinator");
        for (int i = 0; i < clientCount; i++) {
            roles.add("c" + i);
        }
        return roles;
    }

    @Override
    public List<String> visibleRoles() {
        return rolesInSpawnOrder().subList(1, clientCount + 1);
    }

    @Override
    public void run() {
        Tid[] clients = new Tid[clientCount];
        Tid[] server = new Tid[1];
        MpThread serverThread = new MpThread(kind == Kind.SPEC ? () -> lock(clients) : () -> coordinator(clients),
                kind == Kind.SPEC ? "lock" : "coordinator");
        server[0] = Tid.of(serverThread.getTaskId());
        MpThread[] clientThreads = new MpThread[clientCount];
        for (int i = 0; i < clientCount; i++) {
            final int me = i;
            clientThreads[i] = new MpThread(() -> client(me, server[0]), "c" + i);
            clients[i] = Tid.of(clientThreads[i].getTaskId());
        }
        serverThread.start();
        for (MpThread t : clientThreads) {
            t.start();
        }
        try {
            serverThread.join1();
            for (MpThread t : clientThreads) {
                t.join1();
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void client(int i, Tid server) {
        for (int r = 0; r < rounds; r++) {
            Channel.send(CommunicationModel.P2P, server, new Acq(i));
            Channel.recv(CommunicationModel.P2P, Grant.class);
            Channel.send(CommunicationModel.P2P, server, new Rel(i));
        }
    }

    /** The Spec's lock: one holder at a time, any grant order. */
    private void lock(Tid[] clients) {
        int[] round = new int[clientCount];
        for (int n = 0; n < clientCount * rounds; n++) {
            Acq a = Channel.recv(CommunicationModel.P2P, Acq.class);
            Channel.send(CommunicationModel.P2P, clients[a.i()], new Grant(a.i(), round[a.i()]));
            round[a.i()]++;
            Channel.recv(CommunicationModel.P2P, Rel.class);
        }
    }

    /** The Impl's coordinator: one mailbox, a waiter queue, the fault kinds. */
    private void coordinator(Tid[] clients) {
        int[] round = new int[clientCount];
        boolean held = false;
        Deque<Integer> queue = new ArrayDeque<>();
        for (int n = 0; n < 2 * clientCount * rounds; n++) {
            MpMessage m = Channel.recv(CommunicationModel.P2P);
            if (m instanceof Acq a) {
                if (!held || kind == Kind.DOUBLE_GRANT) {
                    held = true;
                    grant(clients, a.i(), round);
                } else {
                    queue.addLast(a.i());
                }
            } else {
                Integer next = kind == Kind.LIFO ? queue.pollLast() : queue.pollFirst();
                if (next == null) {
                    held = false;
                } else if (kind == Kind.NEVER_GRANT && next == clientCount - 1) {
                    held = false;
                } else {
                    grant(clients, next, round);
                }
            }
        }
    }

    private void grant(Tid[] clients, int i, int[] round) {
        int j = kind == Kind.WRONG_ROUND && i == clientCount - 1 ? round[i] + 1 : round[i];
        Channel.send(CommunicationModel.P2P, clients[i], new Grant(i, j));
        round[i]++;
    }
}
