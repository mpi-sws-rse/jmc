package org.mpi_sws.jmc.mp.programs;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * The program of Must §1.3 / Example 3.1 (plan/state/must-algorithm.md §1.3), under {@code p2p}:
 *
 * <pre>
 * T1: send(T3, 1); send(T2, 2)      T2: recv(); send(T3, 3)      T3: recv(); recv()
 * </pre>
 *
 * <p>It has exactly two {@code p2p}-consistent execution graphs — T3 reads {@code 1} then {@code 3},
 * or {@code 3} then {@code 1} — and one under {@code cd}, since T2's send to T3 is
 * {@code porf}-after T1's through T2's receive (Must Example 3.1).
 *
 * <p>Written to the shape plan/jmc-must/SUMMARY-FORMAT.md requires of both sides: main is
 * invisible, spawns every process first and awaits all of them; the roles are {@code "t1"},
 * {@code "t2"}, {@code "t3"}; no spawned process spawns or awaits another. Processes are spawned in
 * the order {@code t3, t2, t1} because a sender needs its destination's {@link Tid}, which exists
 * only once the destination is spawned; the order of spawning is not part of any graph.
 *
 * <p>One instance is run once per iteration by the checker, so it may tally what it observed across
 * iterations: {@link #toString()} reports how often T3 read {@code 1} first, how often {@code 3}
 * first, and how often anything else (which the port forbids, so that count stays {@code 0}). A tally is taken only after both receives, so an iteration in which T3 was blocked
 * (Must's {@code B}) counts in neither. The tally is a plain field written by the task of T3; JMC
 * runs one task at a time and hands control over through futures, so no synchronisation is needed.
 */
public final class MustSection13 implements MpProgram {

    /**
     * The message value {@code v} of the paper's {@code send(t, v)}; encoded as
     * {@code {"t":"Msg","f":[v]}} on both sides.
     *
     * @param v the integer sent
     */
    @Tag("Msg")
    public record Msg(int v) implements MpMessage {}

    /** Iterations in which T3 read {@code 1} first. */
    private int firstOne;

    /** Iterations in which T3 read {@code 3} first. */
    private int firstThree;

    /** Iterations in which T3 read neither {@code 1} nor {@code 3} first (never, if the port is right). */
    private int firstOther;

    /** Creates the program. */
    public MustSection13() {}

    @Override
    public String programName() {
        return "must-1.3";
    }

    @Override
    public String size() {
        return "";
    }

    @Override
    public CommunicationModel model() {
        return CommunicationModel.P2P;
    }

    @Override
    public List<String> rolesInSpawnOrder() {
        return List.of("t3", "t2", "t1");
    }

    @Override
    public void run() {
        MpProcess.Handle t3 =
                MpProcess.spawn(
                        "t3",
                        () -> {
                            MpMessage a = Channel.recv(CommunicationModel.P2P);
                            Channel.recv(CommunicationModel.P2P);
                            int v = ((Msg) a).v();
                            if (v == 1) {
                                firstOne++;
                            } else if (v == 3) {
                                firstThree++;
                            } else {
                                firstOther++;
                            }
                        });
        Tid t3Tid = t3.tid();
        MpProcess.Handle t2 =
                MpProcess.spawn(
                        "t2",
                        () -> {
                            Channel.recv(CommunicationModel.P2P);
                            Channel.send(CommunicationModel.P2P, t3Tid, new Msg(3));
                        });
        Tid t2Tid = t2.tid();
        MpProcess.Handle t1 =
                MpProcess.spawn(
                        "t1",
                        () -> {
                            Channel.send(CommunicationModel.P2P, t3Tid, new Msg(1));
                            Channel.send(CommunicationModel.P2P, t2Tid, new Msg(2));
                        });
        t1.await();
        t2.await();
        t3.await();
    }

    /**
     * How often each of the two outcomes was observed so far.
     *
     * @return {@code "t3 read 1 first: N; 3 first: M; other first: K"}
     */
    @Override
    public String toString() {
        return "t3 read 1 first: " + firstOne + "; 3 first: " + firstThree
                + "; other first: " + firstOther;
    }
}
