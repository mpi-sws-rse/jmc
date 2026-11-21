package org.mpi_sws.jmc.strategies.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.Monitors;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.strategies.mp.MpFailureKind;
import org.mpi_sws.jmc.strategies.mp.MpRandomSchedulingStrategy;

import java.time.Duration;
import java.util.List;

/**
 * Which message-passing strategy supports which communication model and operation: {@code must} explores
 * {@code P2P} and {@code ASYN} and refuses {@code CD}, {@code MBOX} and the {@link Monitors} wrappers (which always
 * notify under {@code CD}) with a message naming the rule; {@code mp-random} samples all four models and monitors.
 * The strategies that are not message-passing ones refuse every message-passing event; {@code
 * MpApiStrategySupportTest} (integration tests) shows that.
 */
class CapabilityMatrixTest {

    private static final String REPORT = "build/test-results/capability-matrix";
    private static final String RULE = "which must does not support (p2p and asyn only)";

    /**
     * A message.
     *
     * @param n its value
     */
    record Ping(int n) implements MpMessage {}

    /** A client sends one message to a server under {@code model}. */
    private static JmcFunctionalTestTarget.Target pair(CommunicationModel model) {
        return () -> MpProcess.runMain(() -> {
            MpProcess.Handle server = MpProcess.spawn("server", () -> Channel.recv(model));
            MpProcess.Handle client = MpProcess.spawn("client",
                    () -> Channel.send(model, server.tid(), new Ping(1)));
            server.await();
            client.await();
        });
    }

    /** As {@link #pair}, under {@code P2P}, with both operations notified to a monitor. */
    private static JmcFunctionalTestTarget.Target monitored() {
        return () -> MpProcess.runMain(() -> {
            CommunicationModel m = CommunicationModel.P2P;
            MpProcess.Handle monitor = MpProcess.spawn("monitor", () -> {
                Channel.recv(CommunicationModel.CD, Monitors.Notification.class);
                Channel.recv(CommunicationModel.CD, Monitors.Notification.class);
            });
            MpProcess.Handle server = MpProcess.spawn("server",
                    () -> Monitors.recvNotified(m, v -> true, monitor.tid()));
            MpProcess.Handle client = MpProcess.spawn("client",
                    () -> Monitors.sendNotified(m, server.tid(), new Ping(1), monitor.tid()));
            server.await();
            client.await();
            monitor.await();
        });
    }

    private static JmcModelCheckerReport mustRun(MustStrategy s, String name, JmcFunctionalTestTarget.Target t)
            throws JmcCheckerException {
        return new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType("must")
                .strategyConstructor(c -> s).numIterations(0).timeout(Duration.ofSeconds(60)).seed(1L)
                .reportPath(REPORT).build())
                .check(new JmcFunctionalTestTarget(name, t));
    }

    private static JmcModelCheckerReport randomRun(String name, JmcFunctionalTestTarget.Target t)
            throws JmcCheckerException {
        MpRandomSchedulingStrategy s = new MpRandomSchedulingStrategy(1L, REPORT);
        return new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType("mp-random")
                .strategyConstructor(c -> s).numIterations(20).seed(1L).reportPath(REPORT).build())
                .check(new JmcFunctionalTestTarget(name, t));
    }

    @Test
    void must_exploresP2pAndAsyn() throws Exception {
        for (CommunicationModel m : List.of(CommunicationModel.P2P, CommunicationModel.ASYN)) {
            MustStrategy s = new MustStrategy(1L, REPORT);
            JmcModelCheckerReport r = mustRun(s, "must-" + m, pair(m));
            assertEquals(1, r.getTotalIterations(), m + ": one execution");
            assertTrue(s.exhausted(), m + ": exhausted");
            assertNull(s.failureKind(), m.toString());
        }
    }

    @Test
    void must_refusesCdAndMbox_namingTheRule() {
        for (CommunicationModel m : List.of(CommunicationModel.CD, CommunicationModel.MBOX)) {
            MustStrategy s = new MustStrategy(1L, REPORT);
            JmcCheckerException e = assertThrows(JmcCheckerException.class, () -> mustRun(s, "must-" + m, pair(m)));
            assertTrue(e.getMessage().contains("uses model " + m + ", " + RULE), e.getMessage());
            assertEquals(MpFailureKind.RULE, s.failureKind(), m + ": a broken rule, not a program failure");
        }
    }

    @Test
    void must_refusesMonitors_namingTheRule() {
        MustStrategy s = new MustStrategy(1L, REPORT);
        JmcCheckerException e = assertThrows(JmcCheckerException.class, () -> mustRun(s, "must-monitors",
                monitored()));
        // The monitor's own receive under CD, or a notification sent under CD, whichever comes first.
        assertTrue(e.getMessage().contains(RULE + "; the Monitors wrappers notify under CD"), e.getMessage());
        assertEquals(MpFailureKind.RULE, s.failureKind());
    }

    @Test
    void mpRandom_samplesEveryModel_andMonitors() throws Exception {
        for (CommunicationModel m : CommunicationModel.values()) {
            JmcModelCheckerReport r = randomRun("mp-random-" + m, pair(m));
            assertEquals(20, r.getTotalIterations(), m.toString());
        }
        assertEquals(20, randomRun("mp-random-monitors", monitored()).getTotalIterations());
    }
}
