package org.mpi_sws.jmc.mp.fixtures.addressing;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.api.util.statements.JmcAssume;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * Fixtures that use only the API from before process creation and assume (spawn, await and constructed
 * {@code MpThread}s), so that they compile on that baseline: the characterisation programs
 * (core {@code JmcAssume.assume(false)} case
 * by case; the five counterexamples in their receive-workaround form; a send to {@code Tid.of(99)}) and
 * the old-API halves of the guard comparisons. Run as {@code MpProcess.runMain(new P4CharPrograms(name))}.
 * The derived expectations are in {@code must/P4CharTest}.
 *
 * <p>Process names below are Must's creation-path names: main is {@code m} (stable 1); main's k-th creation,
 * counting from 0, is {@code m/k} (stable k + 2 when every process is created by main, in this order, in the
 * first iteration).
 */
public final class P4CharPrograms implements Runnable {

    /** A payload. */
    public record I(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;

    /** CE5's guard reads it; blocked A's cleanup sets it. Reset at the start of every execution. */
    private volatile boolean open;

    /**
     * A named program.
     *
     * @param args the program's name
     */
    public P4CharPrograms(String... args) {
        this.name = args[0].trim();
    }

    /** The receive workaround: a blocking receive whose guard accepts nothing. */
    static void never() {
        Channel.recv(M, m -> false);
    }

    @Override
    public void run() {
        open = false;
        switch (name) {
            // ------------------------------------------------ core JmcAssume.assume(false) under Must (§3, last item)
            case "core_assume_main_false" -> JmcAssume.assume(false);
            case "core_assume_main_nondet" -> JmcAssume.assume(Nondet.chooseBoolean());
            case "core_assume_child_awaited" -> MpProcess.spawn("p", () -> JmcAssume.assume(false)).await();
            case "core_assume_other_parked" -> {
                // P (m/0, stable 2) parks on a receive nothing answers; Q (m/1) finishes; main awaits Q, then assumes
                MpProcess.spawn("p", () -> Channel.recv(M));
                MpProcess.spawn("q", () -> { }).await();
                JmcAssume.assume(false);
            }
            // ------------------------------------------------ the five counterexamples, receive workaround
            case "ce1_recv" -> {
                // P blocks; its finally chooses after its Block
                MpProcess.spawn("p", () -> {
                    try {
                        never();
                    } finally {
                        Nondet.chooseIndex(4);
                    }
                }).await();
            }
            case "ce2_recv" -> {
                // P's cleanup suppresses the blocking exception; main's await completes, main chooses
                MpProcess.spawn("p", P4CharPrograms::suppressed).await();
                Nondet.chooseIndex(4);
            }
            case "ce3_recv" -> {
                // B (m/0, public constructor, not started) is started from blocked A's (m/1) cleanup
                MpThread b = new MpThread(() -> Nondet.chooseIndex(4));
                MpProcess.spawn("a", () -> {
                    try {
                        never();
                    } finally {
                        b.start();
                    }
                }).await();
            }
            case "ce4_recv" -> {
                // the program calls runMain itself around a daemon's always-false receive, then chooses
                MpProcess.runMain(() -> MpProcess.spawnDaemon(P4CharPrograms::never));
                Nondet.chooseIndex(4);
            }
            case "ce5_recv" -> {
                // R (m/0) parks with an impure guard; S1, S2 leave a message each; A (m/3) blocks and
                // its cleanup opens R's guard, so the scheduler re-issues R's receive after A's Block
                MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(M, m -> open));
                MpProcess.Handle s1 = MpProcess.spawn("s1", () -> Channel.send(M, r.tid(), new I(1)));
                MpProcess.Handle s2 = MpProcess.spawn("s2", () -> Channel.send(M, r.tid(), new I(2)));
                MpProcess.Handle a = MpProcess.spawn("a", () -> {
                    try {
                        never();
                    } finally {
                        open = true;
                    }
                });
                s1.await();
                s2.await();
                r.await();
                a.await();
            }
            // ------------------------------------------------ a send to a task no process has (§1)
            case "send_99" -> MpProcess.spawn("p", () -> Channel.send(M, Tid.of(99), new I(1))).await();
            // ------------------------------------------------ guards: spawn's event sequence, spawn(role, null)
            case "spawn_two" -> {
                // b (m/0, task 2) receives a's (m/1, task 3) message
                MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.recv(M));
                MpProcess.Handle a = MpProcess.spawn("a", () -> Channel.send(M, b.tid(), new I(1)));
                a.await();
                b.await();
            }
            case "ctor_two" -> {
                // spawn_two through the public constructor: construct, then start at once (spawn's sequence)
                MpThread b = new MpThread(() -> Channel.recv(M), "b");
                b.start();
                Tid bt = Tid.of(b.getTaskId(), "b");
                MpThread a = new MpThread(() -> Channel.send(M, bt, new I(1)), "a");
                a.start();
                join(a);
                join(b);
            }
            case "spawn_null_body" -> {
                try {
                    MpProcess.spawn("a", null);
                    throw new AssertionError("spawn(a, null) returned");
                } catch (NullPointerException e) {
                    if (!"body".equals(e.getMessage())) {
                        throw new AssertionError("spawn(a, null) message " + e.getMessage());
                    }
                }
                MpProcess.Handle a = MpProcess.spawn("a", () -> { });
                if (a.tid().raw() != 2L) {
                    throw new AssertionError("the first allocated process is task " + a.tid().raw() + ", not 2");
                }
                a.await();
            }
            // ------------------------------------------------ creation parameters, old creation paths
            case "creation_old" -> {
                // tasks 2..6: spawn, spawnDaemon, and the three public constructors; none is a create()
                MpProcess.Handle a = MpProcess.spawn("a", () -> { });
                MpProcess.spawnDaemon(() -> { });
                MpThread t4 = new MpThread(() -> { });
                MpThread t5 = new MpThread(() -> { }, "c");
                MpThread t6 = new MpThread(() -> { }, true);
                t4.start();
                t5.start();
                t6.start();
                a.await();
                join(t4);
                join(t5);
            }
            // ------------------------------------------------ the workaround: construct all, then start all
            case "ctor_all_then_start" -> {
                Tid[] ids = new Tid[2];
                MpThread a = new MpThread(() -> Channel.send(M, ids[1], new I(1)), "a");
                MpThread b = new MpThread(() -> Channel.recv(M), "b");
                ids[0] = Tid.of(a.getTaskId(), "a");
                ids[1] = Tid.of(b.getTaskId(), "b");
                a.start();
                b.start();
                join(a);
                join(b);
            }
            default -> throw new IllegalArgumentException("unknown program " + name);
        }
    }

    /** CE2's body: a blocking receive nothing answers, whose blocking exception the cleanup suppresses. */
    @SuppressWarnings("finally")
    private static void suppressed() {
        try {
            never();
        } finally {
            return;
        }
    }

    static void join(MpThread t) {
        try {
            t.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
