package org.mpi_sws.jmc.mp.launch;

import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.summary.SetIndex;
import org.mpi_sws.jmc.mp.summary.SpecificationFile;
import org.mpi_sws.jmc.mp.summary.Summary;
import org.mpi_sws.jmc.mp.summary.SummaryExporter;
import org.mpi_sws.jmc.strategies.SchedulingStrategyFactory;
import org.mpi_sws.jmc.strategies.mp.MpRecordingStrategy;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs one message-passing program through one {@link JmcModelChecker#check} in this JVM, with no
 * instrumentation agent (plan/jmc-must/AGENT-BRIEF.md, rule 3), and prints the run's numbers.
 *
 * <p>The program is a {@link Runnable} that uses only {@code org.mpi_sws.jmc.api.mp}; the API raises
 * every event, so nothing needs rewriting. {@code <program class>} is a class name, constructed
 * with its public no-argument constructor, or {@code <class>:<a>,<b>,...}, constructed with its
 * public {@code (String...)} constructor from the comma-separated arguments (J4: the fixtures take
 * their kind and size this way, so that a run of record states them on the command line). The
 * checker's own report is printed after the run: iterations that ran, iterations the strategy
 * counted as blocked (Must's {@code B}), the wall time measured here, and iterations per second over
 * that time. The program's {@code toString()} is printed last, so a program may report what it
 * observed across iterations.
 *
 * <pre>
 * java -cp &lt;core main + test classes + runtime deps&gt; org.mpi_sws.jmc.mp.launch.MpLauncher \
 *      &lt;program class[:args]&gt; [strategy] [iterations] [seed] [reportPath] [exportFile] [timeoutSeconds]
 *      [--spec &lt;file&gt;]
 * </pre>
 *
 * <p>Defaults: strategy {@code mp-random}, {@code 1000} iterations, a seed from the clock (printed,
 * so a run can be repeated), report path {@code build/mp-launch/&lt;program simple name&gt;} under
 * the working directory.
 *
 * <p><b>Export mode</b> (J1): with {@code exportFile}, the program must implement {@link MpProgram}
 * and the strategy must be {@code mp-random}; the run is driven by an {@link MpRecordingStrategy}
 * built here through {@code strategyConstructor} with the program's roles, and when the run ends
 * the compact summaries (plan/jmc-must/SUMMARY-FORMAT.md) are written to {@code exportFile} in one
 * go, header first: {@code enumeration:"sampled"} when the checker completed, {@code "aborted"}
 * when it failed (the lines written are those of the iterations that completed without error).
 *
 * <p><b>Counting under {@code must}</b> (J2b): without {@code exportFile} (or with {@code -}), a
 * {@code must} run uses the strategy's counting constructor — any {@link Runnable}, no roles, none
 * of the format's shape rules — and prints {@code graphs:} (distinct complete and blocked graphs, and
 * whether the exploration stack ran empty).
 *
 * <p><b>Export under {@code must}</b> (J2; J0 report §4 as amended): the run is driven by a
 * {@link MustStrategy} with the program's roles; the iteration count is {@code 0} (unbounded) and
 * the stopping condition is the {@code timeoutSeconds} argument (default 600), so that a complete
 * enumeration is the normal end. {@code enumeration} is {@code "complete"} only when the strategy
 * reports its exploration stack exhausted; {@code "bounded"} when a positive iteration count was
 * given and the run stopped on it with work remaining; {@code "aborted"} on a timeout or a failure.
 * {@code iterations} and {@code seed} are written for {@code sampled} files only (the format).
 *
 * <p><b>Online mode under {@code must}</b> (J7): {@code --spec <file>}, anywhere in the argument list,
 * loads a specification file (plan/jmc-must/SUMMARY-FORMAT.md; {@code enumeration} must be
 * {@code complete} and {@code visible} must equal the program's visible roles) into a
 * {@link SetIndex}, and the {@link MustStrategy} queries it at each completed graph, stopping at the
 * first uncovered summary. The program must implement {@link MpProgram}; {@code exportFile} may still
 * be given (the implementation's summaries are written as in export mode) or be {@code -}. Printed:
 * {@code spec:}
 * (the file, its program, size, model and tool, lines read, distinct summaries held, duplicates; the
 * model is printed, never compared — Q-J7-3),
 * {@code lookups:}, and {@code verdict:} — {@code conforms} (stack exhausted, every summary covered),
 * {@code reports} with {@code uncovered:} (the summary as a format line) and {@code at:} (its
 * iteration and the index's answer), or {@code partial} (the run ended on its bound, a timeout or a
 * failure before anything uncovered). The verdict is a result: exit {@code 0} for all three.
 *
 * <p>Exit status: {@code 0} when the checker completes; {@code 1} when the run fails — a
 * {@link JmcCheckerException} (an assertion the checker recorded, a strategy fault, an unsupported
 * event) or any other throwable escaping the checker, such as an {@code AssertionError} the checker
 * lets through unwrapped — in both cases after a {@code FAILED:} line; {@code 2} on bad arguments:
 * a refused specification file, wrong count, a class that cannot be loaded or constructed or is not a
 * {@code Runnable}, a non-numeric or non-positive iteration count, a non-numeric seed, or a strategy
 * name {@link SchedulingStrategyFactory} does not know.
 *
 * <p>Known limits of the checker this launcher cannot paper over (J0 report, tester findings F1
 * and F2): an {@code AssertionError} in a <em>spawned</em> process is logged by {@code JmcThread}
 * and never reaches the checker, and an {@code AssertionError} in main on the <em>last</em>
 * iteration is reported as a completed run; both exit {@code 0} here.
 */
public final class MpLauncher {

    private MpLauncher() {}

    /**
     * Entry point; see the class comment for the arguments and the exit status.
     *
     * @param args program class, then optionally strategy, iterations, seed, report path
     */
    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * The launcher's body; separated from {@link #main} so that the exit status is one value.
     *
     * @param args the command-line arguments
     * @return the exit status
     */
    private static int run(String[] rawArgs) {
        // J7: the --spec option is taken out wherever it stands; the rest is positional as before.
        Path specFile = null;
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < rawArgs.length; i++) {
            if (rawArgs[i].equals("--spec")) {
                if (i + 1 >= rawArgs.length) {
                    return usage("--spec needs a file");
                }
                if (specFile != null) {
                    return usage("--spec given twice");
                }
                specFile = Path.of(rawArgs[++i]);
            } else {
                positional.add(rawArgs[i]);
            }
        }
        String[] args = positional.toArray(new String[0]);
        if (args.length < 1 || args.length > 7) {
            return usage(
                    "expected 1 to 7 arguments: <program class[:args]> [strategy=mp-random]"
                            + " [iterations=1000] [seed] [reportPath] [exportFile] [timeoutSeconds]");
        }
        Runnable program;
        Class<?> programClass;
        int colon = args[0].indexOf(':');
        String className = colon < 0 ? args[0] : args[0].substring(0, colon);
        String[] programArgs = colon < 0 ? null : args[0].substring(colon + 1).split(",", -1);
        try {
            programClass = Class.forName(className);
            Object instance = programArgs == null
                    ? programClass.getDeclaredConstructor().newInstance()
                    : programClass.getDeclaredConstructor(String[].class).newInstance((Object) programArgs);
            if (!(instance instanceof Runnable r)) {
                return usage(args[0] + " is not a Runnable");
            }
            program = r;
        } catch (ReflectiveOperationException | LinkageError e) {
            // An InvocationTargetException says nothing itself; its cause is the constructor's.
            Throwable why = e.getCause() != null ? e.getCause() : e;
            return usage("cannot load and construct " + args[0] + ": " + why);
        }
        String strategy = args.length > 1 ? args[1] : "mp-random";
        if (!SchedulingStrategyFactory.isValidStrategy(strategy)) {
            return usage("unknown strategy " + strategy);
        }
        int iterations;
        long seed;
        long timeoutSeconds;
        try {
            iterations = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
            seed = args.length > 3 ? Long.parseLong(args[3]) : System.nanoTime();
            timeoutSeconds = args.length > 6 ? Long.parseLong(args[6]) : 600L;
        } catch (NumberFormatException e) {
            return usage("iterations, seed and timeoutSeconds must be integers: " + e.getMessage());
        }
        boolean must = strategy.equals("must");
        if (iterations < 1 && !(must && iterations == 0)) {
            return usage("iterations must be at least 1, was " + iterations
                    + (must ? "" : " (0, unbounded with a timeout, is for must only)"));
        }
        if (timeoutSeconds < 1) {
            return usage("timeoutSeconds must be at least 1, was " + timeoutSeconds);
        }
        String reportPath =
                args.length > 4 ? args[4] : "build/mp-launch/" + programClass.getSimpleName();
        // "-" (or no sixth argument) means no export: under must, the counting mode (J2b, ruling 3).
        Path exportFile = args.length > 5 && !args[5].equals("-") ? Path.of(args[5]) : null;
        MpRecordingStrategy recorder = null;
        MustStrategy mustStrategy = null;
        MpProgram mpProgram = null;
        if (exportFile != null) {
            if (!(program instanceof MpProgram mp)) {
                return usage(args[0] + " does not implement MpProgram, which an export needs");
            }
            mpProgram = mp;
            if (strategy.equals("mp-random")) {
                recorder = new MpRecordingStrategy(seed, reportPath, mp.rolesInSpawnOrder(), mp.visibleRoles());
            } else if (must) {
                mustStrategy = new MustStrategy(seed, reportPath, mp.rolesInSpawnOrder(), mp.visibleRoles());
            } else {
                return usage("export under " + strategy + " is not supported (mp-random or must)");
            }
        } else if (must) {
            // Counting mode: any Runnable, no roles, no shape rules; prints the graph counts.
            mustStrategy = new MustStrategy(seed, reportPath);
        }
        SpecificationFile.Loaded spec = null;
        if (specFile != null) {
            if (!must) {
                return usage("--spec needs strategy must, not " + strategy);
            }
            if (!(program instanceof MpProgram mp)) {
                return usage(args[0] + " does not implement MpProgram, which the online check needs");
            }
            mpProgram = mp;
            if (exportFile == null) {
                // The online check summarises every graph: the exporting constructor, roles included.
                mustStrategy = new MustStrategy(seed, reportPath, mp.rolesInSpawnOrder(), mp.visibleRoles());
            }
            try {
                spec = SpecificationFile.load(specFile, mp.visibleRoles());
            } catch (IllegalArgumentException | IOException e) {
                return usage("specification " + specFile + " refused: " + e.getMessage());
            }
            mustStrategy.checkAgainst(spec.index());
        }

        System.out.println("program:    " + programClass.getName());
        System.out.println("strategy:   " + strategy);
        System.out.println("iterations: " + (iterations == 0 ? "unbounded, timeout " + timeoutSeconds + " s"
                : iterations + " requested"));
        System.out.println("seed:       " + seed);
        System.out.println("reportPath: " + reportPath);
        if (exportFile != null) {
            System.out.println("exportFile: " + exportFile);
        }
        if (spec != null) {
            // Q-J7-3: the model is printed, never compared (a differing model is fault F1, reportable here).
            System.out.println("spec:       " + spec.path() + " (program " + spec.program() + ", size "
                    + spec.size() + ", model " + spec.model() + ", tool " + spec.tool() + "; " + spec.lines()
                    + " lines, " + spec.index().size() + " distinct summaries held, " + spec.duplicates()
                    + " duplicates)");
        }

        JmcModelCheckerReport report = null;
        String failure = null;
        long startNanos = System.nanoTime();
        try {
            JmcCheckerConfiguration.Builder builder =
                    new JmcCheckerConfiguration.Builder()
                            .strategyType(strategy)
                            .numIterations(iterations)
                            .seed(seed)
                            .reportPath(reportPath);
            if (recorder != null) {
                MpRecordingStrategy r = recorder;
                builder.strategyConstructor(c -> r);
            }
            if (mustStrategy != null) {
                MustStrategy m = mustStrategy;
                builder.strategyConstructor(c -> m);
            }
            if (must) {
                builder.timeout(Duration.ofSeconds(timeoutSeconds));
            }
            JmcCheckerConfiguration config = builder.build();
            report =
                    new JmcModelChecker(config)
                            .check(new JmcFunctionalTestTarget(programClass.getName(),
                                    // J2c: daemons run on after main's body (raises nothing without them)
                                    () -> MpProcess.runMain(program)));
        } catch (JmcCheckerException e) {
            failure = e.getMessage();
        } catch (RuntimeException | Error e) {
            // The checker lets an Error (an AssertionError raised by main) out unwrapped.
            failure = e.toString();
        }
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1e9;

        if (report != null) {
            int total = report.getTotalIterations();
            int blocked = report.getBlockedIterations();
            System.out.println("ran:        " + total + " iterations");
            System.out.println("blocked:    " + blocked + " (counted by the strategy)");
            System.out.println("completed:  " + (total - blocked));
            System.out.printf(
                    Locale.ROOT,
                    "rate:       %.1f iterations/s%n",
                    elapsedSeconds > 0 ? total / elapsedSeconds : Double.NaN);
            if (report.getErrorMessage() != null) {
                System.out.println("error:      " + report.getErrorMessage());
            }
        }
        System.out.printf(Locale.ROOT, "elapsed:    %.3f s%n", elapsedSeconds);
        System.out.println("observed:   " + program);
        if (mustStrategy != null) {
            System.out.println("graphs:     " + mustStrategy.distinctCompleteGraphs() + " distinct complete, "
                    + mustStrategy.distinctBlockedGraphs() + " distinct blocked; stack "
                    + (mustStrategy.exhausted() ? "exhausted" : "not exhausted"));
        }
        if (spec != null) {
            String verdict;
            if (mustStrategy.uncovered().isPresent()) {
                verdict = "reports";
            } else if (failure == null && mustStrategy.exhausted()) {
                verdict = "conforms";
            } else {
                verdict = "partial";
            }
            System.out.println("lookups:    " + mustStrategy.lookups() + " (" + mustStrategy.found() + " found, "
                    + mustStrategy.signatureMisses() + " signature misses, " + mustStrategy.notFound()
                    + " not found)");
            System.out.println("verdict:    " + verdict);
            if (mustStrategy.uncovered().isPresent()) {
                System.out.println("uncovered:  " + mustStrategy.uncovered().get().toJson(1));
                SetIndex.Covers answer = mustStrategy.uncoveredAnswer().orElseThrow();
                System.out.println("at:         iteration " + mustStrategy.uncoveredIteration() + ", "
                        + (answer instanceof SetIndex.Covers.NoSig
                                ? "NoSig (no specification summary has this signature)"
                                : "NotFound (the signature is held, no order is <= the query's)"));
            }
        }
        if (exportFile != null) {
            String enumeration;
            Map<Summary, Integer> summaries;
            int exported;
            int skipped;
            if (recorder != null) {
                enumeration = failure == null ? "sampled" : "aborted";
                summaries = recorder.summaries();
                exported = recorder.exportedIterations();
                skipped = recorder.skippedIterations();
            } else {
                boolean timedOut = report != null && report.getErrorMessage() != null
                        && report.getErrorMessage().contains("due to timeout");
                if (failure != null || timedOut) {
                    enumeration = "aborted";
                } else if (mustStrategy.exhausted()) {
                    enumeration = "complete";
                } else {
                    enumeration = "bounded";
                }
                summaries = mustStrategy.summaries();
                exported = mustStrategy.exportedIterations();
                skipped = mustStrategy.skippedIterations();
            }
            int ran = report != null ? report.getTotalIterations() : exported + skipped;
            List<String> lines = new ArrayList<>();
            lines.add(SummaryExporter.header(
                    mpProgram.programName(), mpProgram.size(), mpProgram.visibleRoles(),
                    mpProgram.model(), enumeration, seed, ran));
            for (Map.Entry<Summary, Integer> e : summaries.entrySet()) {
                lines.add(e.getKey().toJson(e.getValue()));
            }
            try {
                if (exportFile.getParent() != null) {
                    Files.createDirectories(exportFile.getParent());
                }
                Files.write(exportFile, lines, StandardCharsets.UTF_8);
                System.out.println("exported:   " + exported + " iterations as "
                        + summaries.size() + " distinct summaries (" + enumeration + ", "
                        + skipped + " not exported) to " + exportFile);
            } catch (IOException e) {
                failure = (failure == null ? "" : failure + "; ") + "cannot write " + exportFile + ": " + e;
            }
        }
        if (failure != null) {
            System.out.println("FAILED:     " + failure);
            return 1;
        }
        return 0;
    }

    /**
     * Prints a usage error and returns the bad-arguments exit status.
     *
     * @param why what was wrong
     * @return {@code 2}
     */
    private static int usage(String why) {
        System.err.println("MpLauncher: " + why);
        System.err.println(
                "usage: MpLauncher <program class[:args]> [strategy=mp-random] [iterations=1000]"
                        + " [seed] [reportPath] [exportFile] [timeoutSeconds] [--spec <file>]");
        return 2;
    }
}
