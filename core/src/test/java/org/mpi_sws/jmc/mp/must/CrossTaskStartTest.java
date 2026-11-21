package org.mpi_sws.jmc.mp.must;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.must.J2cTest.Run;
import org.mpi_sws.jmc.strategies.must.P4Must;

/**
 * A process thread built with the public {@link MpThread} constructor by one task and started by
 * another. The start event should name the starter, so that the started process inherits the
 * starter's causal past.
 *
 * <p>{@code a} sends {@code V(1)} to {@code b}, then starts {@code c}; {@code c} sends {@code V(2)}
 * to {@code b}; {@code b} receives twice. {@code V(1)} happens before {@code V(2)} (a's send, a's
 * start of c, c's send). In the control {@code a} also creates {@code c}; in the cross-task variant
 * {@code main} creates {@code c} (runtime ids: b 2, c 3, a 4) and {@code a} starts it.
 */
class CrossTaskStartTest {

    record V(int x) implements MpMessage {}

    static void join(MpThread t) {
        try {
            t.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    /** b's body: under causal delivery the first message read must be V(1); p2p does not order them. */
    static Runnable receiver(CommunicationModel m) {
        return () -> {
            V first = Channel.recv(m, V.class);
            Channel.recv(m, V.class);
            if (m == CommunicationModel.CD && first.x() != 1) {
                throw new AssertionError("b read V(" + first.x() + ") before V(1)");
            }
        };
    }

    /** a creates and starts c itself, then awaits it. */
    static Runnable control(CommunicationModel m) {
        return () -> {
            MpProcess.Handle b = MpProcess.spawn("b", receiver(m));
            Tid bt = b.tid();
            MpProcess.Handle a = MpProcess.spawn("a", () -> {
                Channel.send(m, bt, new V(1));
                MpThread c = new MpThread(() -> Channel.send(m, bt, new V(2)), "c");
                c.start();
                join(c);
            });
            a.await();
            b.await();
        };
    }

    /** main creates c, a starts it, main awaits it. */
    static Runnable crossTask(CommunicationModel m) {
        return () -> {
            MpProcess.Handle b = MpProcess.spawn("b", receiver(m));
            Tid bt = b.tid();
            MpThread c = new MpThread(() -> Channel.send(m, bt, new V(2)), "c");
            MpProcess.Handle a = MpProcess.spawn("a", () -> {
                Channel.send(m, bt, new V(1));
                c.start();
            });
            a.await();
            b.await();
            join(c);
        };
    }

    /** Runs {@code program} under mp-random for 500 iterations; returns the checker's failure or null. */
    static Throwable mpRandom(String tag, Runnable program) {
        try {
            new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType("mp-random").numIterations(500)
                    .seed(1L).reportPath("build/test-results/cross-start/" + tag).build())
                    .check(new JmcFunctionalTestTarget(tag, program::run));
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @Test
    @Timeout(120)
    void must_control_startNamesTheCreatorWhoIsTheStarter() {
        P4Must s = new P4Must(1L, "build/test-results/cross-start/control");
        Run r = J2cTest.check("control", J2cTest.main(control(CommunicationModel.P2P)),
                new JmcCheckerConfiguration.Builder().strategyType("must").strategyConstructor(c -> s)
                        .numIterations(10000).seed(1L).reportPath("build/test-results/cross-start/control"), s);
        assertNull(r.thrown(), String.valueOf(r.thrown()));
        assertTrue(s.log().contains("it=0 START_EVENT task=4 startedBy=3"), String.valueOf(s.log()));
    }

    @Test
    @Timeout(120)
    void must_crossTask_startNamesTheStarter() {
        P4Must s = new P4Must(1L, "build/test-results/cross-start/cross");
        Run r = J2cTest.check("cross", J2cTest.main(crossTask(CommunicationModel.P2P)),
                new JmcCheckerConfiguration.Builder().strategyType("must").strategyConstructor(c -> s)
                        .numIterations(10000).seed(1L).reportPath("build/test-results/cross-start/cross"), s);
        assertNull(r.thrown(), String.valueOf(r.thrown()));
        assertTrue(s.log().contains("it=0 START_EVENT task=3 startedBy=4"), String.valueOf(s.log()));
    }

    @Test
    @Timeout(300)
    void mpRandomCausal_control_causalOrderHolds() {
        Throwable t = mpRandom("r-control", control(CommunicationModel.CD));
        assertNull(t, String.valueOf(t));
    }

    @Test
    @Timeout(300)
    void mpRandomCausal_crossTask_causalOrderHolds() {
        Throwable t = mpRandom("r-cross", crossTask(CommunicationModel.CD));
        assertNull(t, String.valueOf(t));
    }
}
