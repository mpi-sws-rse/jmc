package org.mpi_sws.jmc.mp.fixtures.addressing;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.api.util.statements.JmcAssume;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.fixtures.seq.JmcThreadProcess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fixtures for the new API:
 * {@code MpProcess.create}/{@code createDaemon}, {@code Pending.tid}/{@code start}, {@code MpProcess.assume}.
 * They do not compile on f3a5de93. Run as {@code MpProcess.runMain(new P4Programs(name))}. Programs marked
 * <em>counting-only</em> are invalid export shapes (main sends, a process creates a process, a start after main's
 * first await) and are run under Must's counting constructor or mp-random only. Refusals a program catches are
 * recorded in {@link #refusals} for the test to read.
 */
public final class P4Programs implements Runnable {

    /** A payload. */
    public record I(int v) implements MpMessage {}

    /** A payload naming a task. */
    public record Req(Tid from) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    /** The Pending of the previous run, for {@code stale_run_2}; written by {@code stale_run_1}. */
    private static volatile MpProcess.Pending previousRun;

    private final String name;

    /** The previous iteration's Pending, for {@code stale_iteration}. */
    private volatile MpProcess.Pending previousIteration;

    /** CE5's guard reads it; blocked A's cleanup sets it. Reset at the start of every execution. */
    private volatile boolean open;

    /** Set by a statement after a failed assume (which must never run). */
    public volatile boolean afterAssumeRan;

    /** The messages of the refusals the program caught, in order, over all iterations. */
    public final List<String> refusals = Collections.synchronizedList(new ArrayList<>());

    /**
     * A named program.
     *
     * @param args the program's name
     */
    public P4Programs(String... args) {
        this.name = args[0].trim();
    }

    /** Forgets the Pending kept for {@code stale_run_2}. */
    public static void clearPreviousRun() {
        previousRun = null;
    }

    static void never() {
        Channel.recv(M, m -> false);
    }

    @Override
    public void run() {
        open = false;
        switch (name) {
            // ------------------------------------------------------------ addressing before start
            case "addr_peer_before_start" -> {
                // the same construction, start and await order as P4CharPrograms ctor_all_then_start (the workaround)
                MpProcess.Pending a = MpProcess.create("a");
                MpProcess.Pending b = MpProcess.create("b");
                MpProcess.Handle ha = a.start(() -> Channel.send(M, b.tid(), new I(1)));
                MpProcess.Handle hb = b.start(() -> Channel.recv(M));
                ha.await();
                hb.await();
            }
            case "addr_tid_payload" -> {
                // b names itself in a payload to a, created after it; a answers b through that tid
                MpProcess.Pending a = MpProcess.create("a");
                MpProcess.Pending b = MpProcess.create("b");
                MpProcess.Handle ha = a.start(() -> {
                    Req q = Channel.recv(M, Req.class);
                    Channel.send(M, q.from(), new I(1));
                });
                MpProcess.Handle hb = b.start(() -> {
                    Channel.send(M, a.tid(), new Req(b.tid()));
                    Channel.recv(M);
                });
                ha.await();
                hb.await();
            }
            case "addr_main_sends_first" -> {
                // counting-only: main sends to p before p is started; p's receive comes after its start
                MpProcess.Pending p = MpProcess.create("p");
                Channel.send(M, p.tid(), new I(1));
                p.start(() -> Channel.recv(M)).await();
            }
            case "addr_peer_sends_before_target_starts" -> {
                // counting-only: a's send to b is raised (and a awaited) before b is started
                MpProcess.Pending a = MpProcess.create("a");
                MpProcess.Pending b = MpProcess.create("b");
                a.start(() -> Channel.send(M, b.tid(), new I(1))).await();
                b.start(() -> Channel.recv(M)).await();
            }
            case "addr_child_creates" -> {
                // counting-only: a process creates c, sends to it before starting it, then starts and awaits it
                MpProcess.spawn("a", () -> {
                    MpProcess.Pending c = MpProcess.create("c");
                    Channel.send(M, c.tid(), new I(1));
                    c.start(() -> Channel.recv(M)).await();
                }).await();
            }
            case "addr_start_await_sequential" -> {
                // counting-only (a start after main's first await): accepted, the never-started check is at the end
                MpProcess.Pending a = MpProcess.create("a");
                MpProcess.Pending b = MpProcess.create("b");
                a.start(() -> { }).await();
                b.start(() -> { }).await();
            }
            case "addr_daemon_pending" -> {
                // counting-only: main sends to a daemon before starting it; the daemon reads it and finishes
                MpProcess.Pending d = MpProcess.createDaemon();
                Channel.send(M, d.tid(), new I(1));
                d.start(() -> Channel.recv(M));
            }
            // ------------------------------------------------------------ refusals (§1)
            case "ref_non_creator" -> {
                // b (task 2) created by main; a (task 3) tries to start it; then main starts and awaits b
                MpProcess.Pending b = MpProcess.create("b");
                MpProcess.spawn("a", () -> {
                    try {
                        b.start(() -> { });
                        refusals.add("NO REFUSAL");
                    } catch (IllegalStateException e) {
                        refusals.add(e.getMessage());
                    }
                }).await();
                b.start(() -> { }).await();
            }
            case "ref_second_start" -> {
                MpProcess.Pending p = MpProcess.create("a");
                MpProcess.Handle h = p.start(() -> { });
                try {
                    p.start(() -> { });
                    refusals.add("NO REFUSAL");
                } catch (IllegalStateException e) {
                    refusals.add(e.getMessage());
                }
                h.await();
            }
            case "ref_null_body" -> {
                MpProcess.Pending p = MpProcess.create("a");
                try {
                    p.start(null);
                    refusals.add("NO REFUSAL");
                } catch (NullPointerException e) {
                    refusals.add("NPE " + e.getMessage());
                }
                p.start(() -> { }).await();
            }
            case "stale_iteration" -> {
                // a nondet makes two executions under Must; the second uses the first one's Pending
                Nondet.chooseBoolean();
                staleUses(previousIteration);
                MpProcess.Pending p = MpProcess.create("a");
                previousIteration = p;
                p.start(() -> { }).await();
            }
            case "stale_run_1" -> {
                MpProcess.Pending p = MpProcess.create("a");
                previousRun = p;
                p.start(() -> { }).await();
            }
            case "stale_run_2" -> {
                staleUses(previousRun);
                MpProcess.create("a").start(() -> { }).await();
            }
            case "never_started_normal" -> MpProcess.create("a");
            case "never_started_assume" -> {
                MpProcess.create("a");
                MpProcess.assume(false);
                afterAssumeRan = true;
            }
            case "never_started_await" -> {
                // a (task 2) never started; main blocks at its await of q (task 3), which blocks
                MpProcess.create("a");
                MpProcess.spawn("q", P4Programs::never).await();
            }
            case "never_started_drain" -> {
                // a (task 2) never started; main ends through runMain's daemon drain
                MpProcess.create("a");
                MpProcess.spawnDaemon(() -> { });
            }
            case "never_started_daemon" -> MpProcess.createDaemon();
            case "assume_answered" -> {
                MpProcess.assume(false);
                afterAssumeRan = true;
            }
            // ------------------------------------------ JmcThread peers, never-started processes, creation paths
            case "jt_send" -> {
                // mp-random only (Must refuses a JmcThread): s (an MpThread) sends to j, a JmcThread, which
                // raises START but no TASK_CREATED; the send is valid
                JmcThreadProcess.Handle j = JmcThreadProcess.spawn("j", () -> Channel.recv(M));
                Tid jt = j.tid();
                MpProcess.spawn("s", () -> Channel.send(M, jt, new I(1))).await();
                j.await();
            }
            case "never_started_core_assume" -> {
                // a (task 2) never started; main ends by core JmcAssume.assume(false): no FINISH, no parked main
                MpProcess.create("a");
                JmcAssume.assume(false);
                afterAssumeRan = true;
            }
            case "creation_kinds" -> {
                // every creation path; tasks 2..8 in this order
                MpProcess.Handle a = MpProcess.spawn("a", () -> { });                       // 2
                MpProcess.spawnDaemon(() -> { });                                            // 3 daemon
                MpThread t4 = new MpThread(() -> { });                                       // 4
                MpThread t5 = new MpThread(() -> { }, "c");                                  // 5
                MpThread t6 = new MpThread(() -> { }, true);                                 // 6 daemon
                MpProcess.Pending e = MpProcess.create("e");                                 // 7 pending
                MpProcess.Pending d = MpProcess.createDaemon();                              // 8 pending daemon
                t4.start();
                t5.start();
                t6.start();
                MpProcess.Handle he = e.start(() -> { });
                d.start(() -> { });
                a.await();
                P4CharPrograms.join(t4);
                P4CharPrograms.join(t5);
                he.await();
            }
            // ------------------------------------------------------------ assume (§3)
            case "assume_true", "assume_true_absent" -> {
                // b receives a's message; in assume_true both call assume(true) first, which raises nothing
                boolean call = name.equals("assume_true");
                MpProcess.Handle b = MpProcess.spawn("b", () -> {
                    if (call) {
                        MpProcess.assume(true);
                    }
                    Channel.recv(M);
                });
                MpProcess.Handle a = MpProcess.spawn("a", () -> {
                    if (call) {
                        MpProcess.assume(true);
                    }
                    Channel.send(M, b.tid(), new I(1));
                });
                if (call) {
                    MpProcess.assume(true);
                }
                a.await();
                b.await();
            }
            case "assume_main_alone" -> {
                MpProcess.assume(false);
                afterAssumeRan = true;
            }
            case "assume_main_then_child_fails" -> {
                // main parks at its failed assume; a (m/0, task 2) then fails
                MpProcess.Handle a = MpProcess.spawn("a", () -> {
                    throw new AssertionError("boom");
                });
                MpProcess.assume(false);
                afterAssumeRan = true;
                a.await();
            }
            case "assume_daemon" -> MpProcess.spawnDaemon(() -> MpProcess.assume(false));
            case "assume_proc_awaited" -> MpProcess.spawn("p", () -> MpProcess.assume(false)).await();
            // ------------------------------------------------------------ the five counterexamples, assume form
            case "ce1_assume" -> MpProcess.spawn("p", () -> {
                try {
                    MpProcess.assume(false);
                } finally {
                    Nondet.chooseIndex(4);
                }
            }).await();
            case "ce2_assume" -> {
                MpProcess.spawn("p", P4Programs::suppressedAssume).await();
                Nondet.chooseIndex(4);
            }
            case "ce3_assume" -> {
                MpThread b = new MpThread(() -> Nondet.chooseIndex(4));
                MpProcess.spawn("a", () -> {
                    try {
                        MpProcess.assume(false);
                    } finally {
                        b.start();
                    }
                }).await();
            }
            case "ce4_assume" -> {
                MpProcess.runMain(() -> MpProcess.spawnDaemon(() -> MpProcess.assume(false)));
                Nondet.chooseIndex(4);
            }
            case "ce5_assume" -> {
                MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(M, m -> open));
                MpProcess.Handle s1 = MpProcess.spawn("s1", () -> Channel.send(M, r.tid(), new I(1)));
                MpProcess.Handle s2 = MpProcess.spawn("s2", () -> Channel.send(M, r.tid(), new I(2)));
                MpProcess.Handle a = MpProcess.spawn("a", () -> {
                    try {
                        MpProcess.assume(false);
                    } finally {
                        open = true;
                    }
                });
                s1.await();
                s2.await();
                r.await();
                a.await();
            }
            default -> throw new IllegalArgumentException("unknown program " + name);
        }
    }

    /** Uses a Pending of an earlier execution, if any: tid(), then start(); each refusal is recorded. */
    private void staleUses(MpProcess.Pending old) {
        if (old == null) {
            return;
        }
        try {
            old.tid();
            refusals.add("NO REFUSAL tid");
        } catch (IllegalStateException e) {
            refusals.add("tid: " + e.getMessage());
        }
        try {
            old.start(() -> { });
            refusals.add("NO REFUSAL start");
        } catch (IllegalStateException e) {
            refusals.add("start: " + e.getMessage());
        }
    }

    @SuppressWarnings("finally")
    private static void suppressedAssume() {
        try {
            MpProcess.assume(false);
        } finally {
            return;
        }
    }
}
