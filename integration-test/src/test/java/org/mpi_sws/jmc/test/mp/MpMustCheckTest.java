package org.mpi_sws.jmc.test.mp;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.annotations.JmcExpectAssertionFailure;
import org.mpi_sws.jmc.annotations.JmcExpectExecutions;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

/**
 * The {@code must} strategy run as an ordinary {@code @JmcCheck} test, without the launcher and
 * without a specification: the README's message-passing example.
 *
 * <p>Main is an account that starts at 0; two client processes send it a {@code Deposit} and a
 * {@code Withdraw} of 10. The two messages race, so main receives them in either order, and
 * {@code must} explores exactly these two executions. In the one where the withdrawal arrives first
 * the balance goes negative.
 *
 * <p>The communication model is a local variable, not a static field: a static initializer of a
 * class instrumented by the agent runs before the runtime exists.
 */
public class MpMustCheckTest {

    private static final String REPORT_PATH = "build/test-results/jmc-report";

    /** A deposit of {@code amount}. */
    public record Deposit(int amount) implements MpMessage {}

    /** A withdrawal of {@code amount}. */
    public record Withdraw(int amount) implements MpMessage {}

    /**
     * The README's account: main receives both messages, applies them in arrival order, and
     * asserts after each one that the balance is not negative when {@code check} is set.
     */
    private static void account(boolean check) {
        CommunicationModel m = CommunicationModel.P2P;
        Tid account = MpProcess.self();
        MpProcess.Handle a =
                MpProcess.spawn("depositor", () -> Channel.send(m, account, new Deposit(10)));
        MpProcess.Handle b =
                MpProcess.spawn("withdrawer", () -> Channel.send(m, account, new Withdraw(10)));

        int balance = 0;
        for (int i = 0; i < 2; i++) {
            MpMessage msg = Channel.recv(m);
            balance += msg instanceof Deposit d ? d.amount() : -((Withdraw) msg).amount();
            if (check) {
                assertTrue(balance >= 0, "the balance went negative");
            }
        }
        a.await();
        b.await();
    }

    /** Both arrival orders are explored, and the exploration stops there. */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "must", numIterations = 100)
    @JmcExpectExecutions(2)
    @SuppressWarnings("checkstyle:MethodName")
    public void account_underMust_exploresBothOrders() {
        account(false);
    }

    /** The order in which the withdrawal arrives first fails main's assertion. */
    @JmcCheck
    @JmcCheckConfiguration(strategy = "must", numIterations = 100)
    @JmcExpectAssertionFailure
    @SuppressWarnings("checkstyle:MethodName")
    public void account_underMust_findsNegativeBalance() {
        account(true);
    }

    /**
     * The same assertion failing in a spawned process, not in main, fails the run too. It is a
     * plain test driving the checker, because {@code @JmcExpectAssertionFailure} recognises only an
     * assertion error thrown by main: a failing process reaches the checker as a message naming it.
     */
    @Test
    @SuppressWarnings("checkstyle:MethodName")
    public void account_inSpawnedProcess_underMust_failsTheRun() throws Exception {
        JmcModelChecker checker = new JmcModelChecker(new JmcCheckerConfiguration.Builder()
                .strategyType("must").numIterations(100).seed(1L).reportPath(REPORT_PATH).build());
        JmcCheckerException e = assertThrows(JmcCheckerException.class, () -> checker.check(
                new JmcFunctionalTestTarget("account-process", () -> {
                    MpProcess.Handle acct = MpProcess.spawn("account", () -> account(true));
                    acct.await();
                })));
        String why = String.valueOf(e.getMessage());
        assertTrue(why.contains("process") && why.contains("the balance went negative"), why);
    }
}
