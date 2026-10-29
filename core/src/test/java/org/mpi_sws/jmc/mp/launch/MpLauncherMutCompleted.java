package org.mpi_sws.jmc.mp.launch;

import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;

import java.util.Locale;

/**
 * TESTER MUTANT (J0 round 01): a copy of MpLauncher whose "completed" line prints ran, not ran - blocked.
 *
 *
 * Runs one message-passing program through one {@link JmcModelChecker#check} in this JVM, with no
 * instrumentation agent (plan/jmc-must/AGENT-BRIEF.md, rule 3), and prints the run's numbers.
 *
 * <p>The program is a {@link Runnable} with a public no-argument constructor that uses only
 * {@code org.mpi_sws.jmc.api.mp}; the API raises every event, so nothing needs rewriting. The
 * checker's own report is printed after the run: iterations that ran, iterations the strategy
 * counted as blocked (Must's {@code B}), the wall time measured here, and iterations per second over
 * that time. The program's {@code toString()} is printed last, so a program may report what it
 * observed across iterations.
 *
 * <pre>
 * java -cp &lt;core main + test classes + runtime deps&gt; org.mpi_sws.jmc.mp.launch.MpLauncher \
 *      &lt;program class&gt; [strategy] [iterations] [seed] [reportPath]
 * </pre>
 *
 * <p>Defaults: strategy {@code mp-random}, {@code 1000} iterations, a seed from the clock (printed,
 * so a run can be repeated), report path {@code build/mp-launch/&lt;program simple name&gt;} under
 * the working directory. Exit status {@code 0} when the checker completes, {@code 1} when it fails
 * (an assertion, a strategy fault, an unsupported event), {@code 2} on bad arguments.
 */
public final class MpLauncherMutCompleted {

    private MpLauncherMutCompleted() {}

    /**
     * Entry point; see the class comment for the arguments.
     *
     * @param args program class, then optionally strategy, iterations, seed, report path
     * @throws ReflectiveOperationException if the program class cannot be loaded or constructed
     */
    public static void main(String[] args) throws ReflectiveOperationException {
        if (args.length < 1 || args.length > 5) {
            System.err.println(
                    "usage: MpLauncher <program class> [strategy=mp-random] [iterations=1000]"
                            + " [seed] [reportPath]");
            System.exit(2);
        }
        Class<?> programClass = Class.forName(args[0]);
        Object instance = programClass.getDeclaredConstructor().newInstance();
        if (!(instance instanceof Runnable program)) {
            System.err.println(args[0] + " is not a Runnable");
            System.exit(2);
            return;
        }
        String strategy = args.length > 1 ? args[1] : "mp-random";
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
        long seed = args.length > 3 ? Long.parseLong(args[3]) : System.nanoTime();
        String reportPath =
                args.length > 4 ? args[4] : "build/mp-launch/" + programClass.getSimpleName();

        System.out.println("program:    " + programClass.getName());
        System.out.println("strategy:   " + strategy);
        System.out.println("iterations: " + iterations + " requested");
        System.out.println("seed:       " + seed);
        System.out.println("reportPath: " + reportPath);

        JmcModelCheckerReport report = null;
        String failure = null;
        long startNanos = System.nanoTime();
        try {
            JmcCheckerConfiguration config =
                    new JmcCheckerConfiguration.Builder()
                            .strategyType(strategy)
                            .numIterations(iterations)
                            .seed(seed)
                            .reportPath(reportPath)
                            .build();
            report =
                    new JmcModelChecker(config)
                            .check(new JmcFunctionalTestTarget(programClass.getName(), program::run));
        } catch (JmcCheckerException e) {
            failure = e.getMessage();
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedSeconds = elapsedNanos / 1e9;

        if (report != null) {
            int total = report.getTotalIterations();
            int blocked = report.getBlockedIterations();
            System.out.println("ran:        " + total + " iterations");
            System.out.println("blocked:    " + blocked + " (counted by the strategy)");
            System.out.println("completed:  " + (total));
            System.out.printf(Locale.ROOT, "elapsed:    %.3f s%n", elapsedSeconds);
            System.out.printf(
                    Locale.ROOT,
                    "rate:       %.1f iterations/s%n",
                    elapsedSeconds > 0 ? total / elapsedSeconds : Double.NaN);
            if (report.getErrorMessage() != null) {
                System.out.println("error:      " + report.getErrorMessage());
            }
        }
        System.out.println("observed:   " + program);
        if (failure != null) {
            System.out.printf(Locale.ROOT, "elapsed:    %.3f s%n", elapsedSeconds);
            System.out.println("FAILED:     " + failure);
            System.exit(1);
        }
    }
}
