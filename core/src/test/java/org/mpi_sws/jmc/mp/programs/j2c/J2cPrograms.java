package org.mpi_sws.jmc.mp.programs.j2c;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * Tester fixtures for J2c round 01 (daemon processes; plan/jmc-must/log/dev/J2c/round-01.prompt.md).
 * Run as {@code MpProcess.runMain(new J2cPrograms(name))}, except where a test says otherwise. The
 * derived counts are in {@code must/J2cTest} and the report.
 */
public final class J2cPrograms implements Runnable {

    /** A payload. */
    public record I(int v) implements MpMessage {}

    /** A payload naming a task. */
    public record Req(Tid from) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String name;
    /** The message of the refused await of a daemon (ruling 5), for the test to read. */
    public volatile String refusal;

    /**
     * A named program.
     *
     * @param args the program's name
     */
    public J2cPrograms(String... args) {
        this.name = args[0].trim();
    }

    static void join(MpThread t) {
        try {
            t.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void run() {
        Tid me = MpProcess.self();
        switch (name) {
            // ---------------------------------------------------------------- ruling 1
            case "r1_daemon_recv" -> MpProcess.spawnDaemon(() -> Channel.recv(M));
            case "r1_daemon_await_blocked" -> {
                MpThread p = new MpThread(() -> Channel.recv(M));
                p.start();
                MpProcess.spawnDaemon(() -> join(p));
            }
            case "r1_nondaemon_recv" -> {
                MpThread p = new MpThread(() -> Channel.recv(M));
                p.start();
                join(p);
            }
            case "r1_mixed_complete", "r1_mixed_blocked" -> {
                // D1 blocks on a receive; D2 receives P's message and finishes; P is awaited. In the
                // blocked variant a non-daemon Q also blocks on a receive (not awaited).
                MpProcess.spawnDaemon(() -> Channel.recv(M));
                Tid d2 = MpProcess.spawnDaemon(() -> Channel.recv(M)).tid();
                MpThread p = new MpThread(() -> Channel.send(M, d2, new I(1)));
                p.start();
                if (name.equals("r1_mixed_blocked")) {
                    new MpThread(() -> Channel.recv(M)).start();
                }
                join(p);
            }
            // ---------------------------------------------------------------- ruling 2
            case "r2_daemon_finishes" -> {
                Tid d = MpProcess.spawnDaemon(() -> Channel.recv(M)).tid();
                Channel.send(M, d, new I(1));
            }
            case "r2_daemon_blocks" -> {
                Tid d = MpProcess.spawnDaemon(() -> {
                    Channel.recv(M);
                    Channel.recv(M);
                }).tid();
                Channel.send(M, d, new I(1));
            }
            case "r2_daemon_to_daemon" -> {
                Tid d2 = MpProcess.spawnDaemon(() -> Channel.recv(M)).tid();
                Tid d1 = MpProcess.spawnDaemon(() -> Channel.send(M, d2, Channel.recv(M))).tid();
                Channel.send(M, d1, new I(1));
            }
            // ---------------------------------------------------------------- ruling 3
            case "r3_daemon_nondet" -> MpProcess.spawnDaemon(() -> Nondet.chooseBoolean());
            case "r3_daemon_rf" -> {
                Tid d = MpProcess.spawnDaemon(() -> Channel.recv(M)).tid();
                MpThread s1 = new MpThread(() -> Channel.send(M, d, new I(1)));
                MpThread s2 = new MpThread(() -> Channel.send(M, d, new I(2)));
                s1.start();
                s2.start();
                join(s1);
                join(s2);
            }
            // ---------------------------------------------------------------- ruling 5
            case "r5_await_daemon_caught" -> {
                MpProcess.Handle d = MpProcess.spawnDaemon(() -> { });
                try {
                    d.await();
                } catch (IllegalStateException e) {
                    refusal = e.getMessage();
                }
            }
            case "r5_await_daemon" -> MpProcess.spawnDaemon(() -> { }).await();
            // ---------------------------------------------------------------- ruling 6
            case "r6_daemon_fails" -> MpProcess.spawnDaemon(() -> {
                throw new IllegalStateException("d fails");
            });
            // ---------------------------------------------------------------- ruling 7
            case "r7_daemon_nb" -> {
                Tid d = MpProcess.spawnDaemon(() -> Channel.tryRecv(M)).tid();
                MpThread s = new MpThread(() -> Channel.send(M, d, new I(1)));
                s.start();
                join(s);
            }
            case "r7_daemon_unread" -> {
                Tid d = MpProcess.spawnDaemon(() -> { }).tid();
                Channel.send(M, d, new I(1));
                Channel.send(M, d, new I(2));
            }
            // ---------------------------------------------------------------- round 03: review M1, m1
            case "m1_daemon_finished_await_blocked", "m1_daemon_recv_await_blocked" -> {
                if (name.equals("m1_daemon_finished_await_blocked")) {
                    MpProcess.spawnDaemon(() -> { });
                } else {
                    MpProcess.spawnDaemon(() -> Channel.recv(M));
                }
                MpProcess.spawn("p", () -> Channel.recv(M)).await();
            }
            case "m1_daemon_runs_to_end" -> {
                // P (created first) blocks; D does four events; main awaits P. A task is blocked only
                // when nothing can run, so D runs to its end before P's Block.
                MpProcess.Handle p = MpProcess.spawn("p", () -> Channel.recv(M));
                MpProcess.spawnDaemon(() -> {
                    Tid self = MpProcess.self();
                    Channel.send(M, self, new I(1));
                    Channel.recv(M);
                    Channel.send(M, self, new I(2));
                    Channel.recv(M);
                });
                p.await();
            }
            case "m1_ctor_daemon_recv" -> new MpThread(() -> Channel.recv(M), true).start();
            // ---------------------------------------------------------------- without runMain
            case "no_runmain" -> {
                Tid d = MpProcess.spawnDaemon(() -> Channel.recv(M)).tid();
                Channel.send(M, d, new I(1));
            }
            default -> throw new IllegalArgumentException("unknown program " + name);
        }
    }
}
