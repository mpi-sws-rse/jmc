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
import org.mpi_sws.jmc.strategies.must.split.SplitCoordinator;
import org.mpi_sws.jmc.strategies.must.split.SplitWorker;

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
 * {@link SetIndex}, and the {@link MustStrategy} queries it at each completed graph. By default (full
 * mode) the exploration continues past an uncovered summary, keeping the first one and counting every
 * uncovered graph; with {@code --stop-at-first} (bug-finder mode) it stops at the first. The program
 * must implement {@link MpProgram}; {@code exportFile} may still be given (the implementation's
 * summaries are written as in export mode, with {@code first_miss_iteration}, {@code
 * first_miss_wall_ms} and {@code uncovered_total} in the header) or be {@code -}. Printed:
 * {@code spec:}
 * (the file, its program, size, model and tool, lines read, distinct summaries held, duplicates; the
 * model is printed, never compared),
 * {@code lookups:}, {@code verdict:} — {@code conforms} (stack exhausted, every summary covered),
 * {@code reports} (something uncovered) or {@code partial} (the run ended on its bound, a timeout or a
 * failure before anything uncovered) — {@code mode:}, {@code first miss: iteration I, T ms} (on a
 * miss), {@code uncovered: U graphs (D distinct summaries)}, and on a miss {@code witness:} (the first
 * uncovered summary as a format line) and {@code at:} (its iteration and the index's answer). The
 * verdict is a result: exit {@code 0} for all three.
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

    /** The line that opens the block of measurement lines ({@link #printMeasurements}). */
    static final String MEASUREMENTS_MARKER = "--- measurements";

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
        Measured st = new Measured(System.nanoTime());
        int status;
        try {
            status = runBody(rawArgs, st);
            if (status != 0 && st.failure == null) {
                // An unsuccessful return the body did not describe (a rejected argument after the
                // strategy existed): a failed invocation, never an ordinary stop.
                st.failure = "launcher exit status " + status;
            }
        } catch (RuntimeException | Error e) {
            // A failure outside the checker (e.g. while writing the output): still one measurement block.
            st.failure = (st.failure == null ? "" : st.failure + "; ") + e;
            System.out.println("FAILED:     " + e);
            status = 1;
        } finally {
            if (st.must != null && !st.printed) {
                printMeasurements(st);
            }
        }
        return status;
    }

    /** What the measurement block of a must run is computed from; filled as the run proceeds. */
    private static final class Measured {
        final long wallStartNanos;
        MustStrategy must;
        boolean online;
        JmcModelCheckerReport report;
        String failure;
        long checkNanos;
        long indexNanos;
        long exportNanos;
        boolean printed;

        Measured(long wallStartNanos) {
            this.wallStartNanos = wallStartNanos;
        }
    }

    /**
     * The launcher's body. A must run's measurement block is printed here before the final verdict
     * line, or by {@link #run} if the body ends early (a refused specification) or throws.
     *
     * @param rawArgs the command-line arguments
     * @param st the run's measured state
     * @return the exit status
     */
    private static int runBody(String[] rawArgs, Measured st) {
        // J7: the --spec option is taken out wherever it stands; the rest is positional as before.
        Path specFile = null;
        boolean stopAtFirst = false;
        Integer splitWorkers = null;
        Integer splitK = null;
        Path workerDir = null;
        Integer workerId = null;
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < rawArgs.length; i++) {
            if (rawArgs[i].equals("--split") || rawArgs[i].equals("--k")) {
                // J8: the split options, like --spec, may stand anywhere after the program.
                if (i + 1 >= rawArgs.length) {
                    return usage(rawArgs[i] + " needs a number");
                }
                int value;
                try {
                    value = Integer.parseInt(rawArgs[i + 1]);
                } catch (NumberFormatException e) {
                    return usage(rawArgs[i] + " needs a number, not " + rawArgs[i + 1]);
                }
                if (value < 1) {
                    return usage(rawArgs[i] + " must be at least 1, was " + value);
                }
                if (rawArgs[i].equals("--split")) {
                    splitWorkers = value;
                } else {
                    splitK = value;
                }
                i++;
            } else if (rawArgs[i].equals("--worker")) {
                if (i + 2 >= rawArgs.length) {
                    return usage("--worker needs a run directory and a worker id");
                }
                workerDir = Path.of(rawArgs[i + 1]);
                try {
                    workerId = Integer.parseInt(rawArgs[i + 2]);
                } catch (NumberFormatException e) {
                    return usage("--worker needs a numeric worker id, not " + rawArgs[i + 2]);
                }
                i += 2;
            } else if (rawArgs[i].equals("--stop-at-first")) {
                // The online check's bug-finder mode; without it the check runs in full mode.
                stopAtFirst = true;
            } else if (rawArgs[i].equals("--spec")) {
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
        if (stopAtFirst && specFile == null) {
            return usage("--stop-at-first needs --spec");
        }
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
        st.must = mustStrategy;
        st.online = must && specFile != null;
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
                st.must = mustStrategy;
            }
            long indexStartNanos = System.nanoTime();
            try {
                spec = SpecificationFile.load(specFile, mp.visibleRoles());
            } catch (IllegalArgumentException | IOException e) {
                st.failure = "specification " + specFile + " refused: " + e.getMessage();
                return usage(st.failure);
            } finally {
                st.indexNanos = System.nanoTime() - indexStartNanos;
            }
            mustStrategy.checkAgainst(spec.index(), stopAtFirst);
        }

        if (splitWorkers != null || splitK != null || workerDir != null) {
            // The split run prints its own measurement block (the coordinator; a worker prints none).
            st.must = null;
            if (!must) {
                return usage("--split, --k and --worker need strategy must, not " + strategy);
            }
            if (specFile != null) {
                return usage("--spec cannot be combined with --split (no online check in split mode)");
            }
            if (iterations != 0) {
                return usage("split mode is exhaustive: iterations must be 0, was " + iterations);
            }
            if (splitK != null && splitWorkers == null) {
                return usage("--k needs --split");
            }
            List<String> roles = mpProgram == null ? null : mpProgram.rolesInSpawnOrder();
            List<String> visible = mpProgram == null ? null : mpProgram.visibleRoles();
            if (workerDir != null) {
                return SplitWorker.run(workerDir, workerId, program, roles, visible);
            }
            return runSplit(st, args, rawArgs, program, programClass, mpProgram, splitWorkers,
                    splitK == null ? 8 * splitWorkers : splitK, seed, reportPath, exportFile, timeoutSeconds);
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
        st.checkNanos = System.nanoTime() - startNanos;
        st.report = report;
        st.failure = failure;

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
            System.out.println("mode:       "
                    + (mustStrategy.stopsAtFirst() ? "bug-finder (--stop-at-first)" : "full"));
            if (mustStrategy.uncovered().isPresent()) {
                System.out.println("first miss: iteration " + mustStrategy.uncoveredIteration() + ", "
                        + Math.max(0, mustStrategy.measurements().firstMissWallMs()) + " ms");
            }
            System.out.println("uncovered:  " + mustStrategy.uncoveredTotal() + " graphs ("
                    + mustStrategy.uncoveredDistinct() + " distinct summaries)");
            if (mustStrategy.uncovered().isPresent()) {
                System.out.println("witness:    " + mustStrategy.uncovered().get().toJson(1));
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
            SummaryExporter.OnlineFields online = spec == null ? null : new SummaryExporter.OnlineFields(
                    mustStrategy.uncoveredIteration(), Math.max(0, mustStrategy.measurements().firstMissWallMs()),
                    mustStrategy.uncoveredTotal());
            lines.add(SummaryExporter.header(
                    mpProgram.programName(), mpProgram.size(), mpProgram.visibleRoles(),
                    mpProgram.model(), enumeration, seed, ran, online));
            for (Map.Entry<Summary, Integer> e : summaries.entrySet()) {
                lines.add(e.getKey().toJson(e.getValue()));
            }
            try {
                if (exportFile.getParent() != null) {
                    Files.createDirectories(exportFile.getParent());
                }
                long exportStartNanos = System.nanoTime();
                try {
                    Files.write(exportFile, lines, StandardCharsets.UTF_8);
                } finally {
                    st.exportNanos = System.nanoTime() - exportStartNanos;
                }
                System.out.println("exported:   " + exported + " iterations as "
                        + summaries.size() + " distinct summaries (" + enumeration + ", "
                        + skipped + " not exported) to " + exportFile);
            } catch (IOException e) {
                failure = (failure == null ? "" : failure + "; ") + "cannot write " + exportFile + ": " + e;
            }
        }
        if (mustStrategy != null) {
            st.failure = failure;
            printMeasurements(st);
        }
        if (failure != null) {
            System.out.println("FAILED:     " + failure);
            return 1;
        }
        return 0;
    }

    /**
     * Prints the run's measurements as machine-readable {@code key: value} lines, one per key, in a
     * fixed order, after the marker line {@value #MEASUREMENTS_MARKER}. Times are wall-clock milliseconds; a phase that does not apply prints 0.
     *
     * <ul>
     *   <li>{@code wall_ms}: from the launcher's start to these lines;</li>
     *   <li>{@code explore_ms}: the checker's run minus {@code summarise_ms} and {@code lookup_ms};</li>
     *   <li>{@code summarise_ms}, {@code lookup_ms}: graph to summary, and coverage queries
     *       (cumulative, on the checker's thread); {@code index_ms}: loading the specification file
     *       into the Set index; {@code export_ms}: writing the summary file;</li>
     *   <li>{@code graphs_complete}, {@code graphs_blocked}: distinct graphs; {@code summaries_distinct};
     *       {@code iterations}: iterations that ran;</li>
     *   <li>{@code stop_reason}, by precedence: {@code error} (the run failed), {@code timeout}, then
     *       {@code first-miss}, {@code exhausted}, {@code budget} (the iteration limit); in detail:
     *       {@code exhausted}, {@code budget},
     *       {@code timeout}, {@code first-miss} (bug-finder mode only: stopped at the first uncovered
     *       summary, also when it was the last graph; in full mode a miss never stops the run) or
     *       {@code error} (the run failed);</li>
     *   <li>{@code peak_rss_kb}: the process's peak resident set ({@code VmHWM}), empty if unavailable;
     *       {@code max_message_events}: send and receive events of all threads in the largest graph;
     *       {@code max_porf_pairs}: the most non-zero order-count entries in one summary;</li>
     *   <li>with a specification file, {@code first_miss_iteration} (1-based, 0 for none),
     *       {@code first_miss_wall_ms} (since the start of the exploration, 0 for none) and
     *       {@code uncovered_total};</li>
     *   <li>the graph-shape counters over the explored graphs: {@code max_graph_events} (every
     *       event), {@code mean_graph_events}, {@code max_visible_events} and {@code
     *       mean_visible_events} (send and receive events of visible roles), {@code max_summary_bytes}
     *       (the summary line without its multiplicity), {@code max_porf_count} (the largest single
     *       order-count entry). Definitions as in the evaluation schema (brain-data/SCHEMA.md).</li>
     * </ul>
     */
    private static void printMeasurements(Measured st) {
        st.printed = true;
        MustStrategy m = st.must;
        JmcModelCheckerReport report = st.report;
        MustStrategy.Measurements x = m.measurements();
        boolean timedOut = report != null && report.getErrorMessage() != null
                && report.getErrorMessage().contains("due to timeout");
        // Precedence: a failed run is an error whatever else happened (a miss is still reported through
        // the first_miss_* keys); then a timeout; then a miss that stopped the run.
        String stopReason;
        if (st.failure != null) {
            stopReason = "error";
        } else if (timedOut) {
            stopReason = "timeout";
        } else if (m.stopsAtFirst() && m.uncovered().isPresent()) {
            // The run stopped because of the miss, even when no work was left (a miss on the last graph).
            stopReason = "first-miss";
        } else if (m.exhausted()) {
            stopReason = "exhausted";
        } else {
            stopReason = "budget";
        }
        // The block starts after this marker; an earlier human-readable line may share a key's name
        // ("iterations:"), so a reader takes the keys after the marker only.
        System.out.println(MEASUREMENTS_MARKER);
        System.out.println("wall_ms: " + (System.nanoTime() - st.wallStartNanos) / 1_000_000);
        System.out.println("explore_ms: "
                + Math.max(0, st.checkNanos - x.summariseNanos() - x.lookupNanos()) / 1_000_000);
        System.out.println("summarise_ms: " + x.summariseNanos() / 1_000_000);
        System.out.println("index_ms: " + st.indexNanos / 1_000_000);
        System.out.println("lookup_ms: " + x.lookupNanos() / 1_000_000);
        System.out.println("export_ms: " + st.exportNanos / 1_000_000);
        System.out.println("graphs_complete: " + m.distinctCompleteGraphs());
        System.out.println("graphs_blocked: " + m.distinctBlockedGraphs());
        System.out.println("summaries_distinct: " + m.summaries().size());
        System.out.println("iterations: " + (report != null ? report.getTotalIterations() : m.iterations()));
        System.out.println("stop_reason: " + stopReason);
        long rss = peakRssKb();
        // Empty when unavailable: the measurement applies but could not be read.
        System.out.println("peak_rss_kb: " + (rss < 0 ? "" : Long.toString(rss)));
        System.out.println("max_message_events: " + x.maxMessageEvents());
        System.out.println("max_porf_pairs: " + x.maxPorfPairs());
        if (st.online) {
            System.out.println("first_miss_iteration: " + m.uncoveredIteration());
            System.out.println("first_miss_wall_ms: " + Math.max(0, x.firstMissWallMs()));
            System.out.println("uncovered_total: " + m.uncoveredTotal());
        }
        System.out.println("max_graph_events: " + x.maxGraphEvents());
        System.out.printf(Locale.ROOT, "mean_graph_events: %.3f%n", x.meanGraphEvents());
        System.out.println("max_visible_events: " + x.maxVisibleEvents());
        System.out.printf(Locale.ROOT, "mean_visible_events: %.3f%n", x.meanVisibleEvents());
        System.out.println("max_summary_bytes: " + x.maxSummaryBytes());
        System.out.println("max_porf_count: " + x.maxPorfCount());
    }

    /** The process's peak resident set in kB ({@code VmHWM} of {@code /proc/self/status}), -1 if unavailable. */
    private static long peakRssKb() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"), StandardCharsets.UTF_8)) {
                if (line.startsWith("VmHWM:")) {
                    return Long.parseLong(line.replaceAll("[^0-9]", ""));
                }
            }
        } catch (IOException | RuntimeException e) {
            // Not Linux, or not readable: reported as unavailable.
        }
        return -1;
    }

    /**
     * The coordinator of a split run ({@code must --split W [--k K]}): runs the root here, starts
     * {@code W} worker JVMs (this launcher with {@code --worker}, the same class path and JVM options),
     * merges, and prints and exports as a sequential {@code must} run does. The run directory is
     * {@code <reportPath>/split-<random id>}.
     *
     * @return {@code 0} for a complete run, {@code 1} if it aborted (incomplete; nothing is exported)
     */
    private static int runSplit(Measured st, String[] args, String[] rawArgs, Runnable program,
                                Class<?> programClass,
                                MpProgram mpProgram, int workers, int k, long seed, String reportPath,
                                Path exportFile, long timeoutSeconds) {
        String identity = args[0] + (mpProgram == null ? "" : "|" + mpProgram.model());
        Path runDir = Path.of(reportPath, "split-" + java.util.UUID.randomUUID());
        List<String> jvmOptions = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classPath = System.getProperty("java.class.path");
        System.out.println("program:    " + programClass.getName());
        System.out.println("strategy:   must");
        System.out.println("iterations: unbounded, timeout " + timeoutSeconds + " s");
        System.out.println("seed:       " + seed);
        System.out.println("reportPath: " + reportPath);
        if (exportFile != null) {
            System.out.println("exportFile: " + exportFile);
        }
        long startNanos = System.nanoTime();
        SplitCoordinator.Result result;
        try {
            result = SplitCoordinator.run(new SplitCoordinator.Config(identity, program,
                    mpProgram == null ? null : mpProgram.rolesInSpawnOrder(),
                    mpProgram == null ? null : mpProgram.visibleRoles(), k, workers, timeoutSeconds, runDir,
                    (dir, id) -> {
                        List<String> cmd = new ArrayList<>();
                        cmd.add(java);
                        cmd.addAll(jvmOptions);
                        cmd.add("-cp");
                        cmd.add(classPath);
                        cmd.add(MpLauncher.class.getName());
                        cmd.add(args[0]);
                        cmd.add("must");
                        cmd.add("0");
                        cmd.add(Long.toString(seed));
                        cmd.add(reportPath);
                        cmd.add(exportFile == null ? "-" : exportFile.toString());
                        cmd.add(Long.toString(timeoutSeconds));
                        cmd.add("--worker");
                        cmd.add(dir.toString());
                        cmd.add(Integer.toString(id));
                        return cmd;
                    }));
        } catch (IOException e) {
            System.out.println("FAILED:     cannot prepare " + runDir + ": " + e);
            return 1;
        }
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1e9;
        System.out.println("split:      K " + k + ", W " + workers + ", R " + result.r() + ", N " + result.n()
                + (result.split() ? "" : " (no split: the root exhausted the stack)") + "; run directory " + runDir);
        if (result.failure() != null) {
            System.out.printf(Locale.ROOT, "elapsed:    %.3f s%n", elapsedSeconds);
            printSplitMeasurements(st, result, 0, false);
            System.out.println("FAILED:     incomplete: " + result.failure());
            return 1;
        }
        System.out.println("ran:        " + result.ran() + " iterations");
        System.out.println("blocked:    " + result.blockedIterations() + " (counted by the strategy)");
        System.out.println("completed:  " + (result.ran() - result.blockedIterations()));
        System.out.println("items:      " + result.itemIterations() + (result.workersLost() == 0 ? ""
                : "; " + result.workersLost() + " worker(s) lost, the run continued without them"));
        System.out.printf(Locale.ROOT, "elapsed:    %.3f s%n", elapsedSeconds);
        System.out.println("observed:   " + program);
        System.out.println("graphs:     " + result.distinctComplete() + " distinct complete, "
                + result.distinctBlocked() + " distinct blocked; stack exhausted");
        long exportNanos = 0;
        if (exportFile != null) {
            List<String> lines = new ArrayList<>();
            lines.add(SummaryExporter.header(mpProgram.programName(), mpProgram.size(), mpProgram.visibleRoles(),
                    mpProgram.model(), "complete", seed, result.ran()));
            lines.addAll(result.summaryLines());
            long exportStart = System.nanoTime();
            try {
                if (exportFile.getParent() != null) {
                    Files.createDirectories(exportFile.getParent());
                }
                Files.write(exportFile, lines, StandardCharsets.UTF_8);
                System.out.println("exported:   " + result.ran() + " iterations as " + result.summaryLines().size()
                        + " distinct summaries (complete, 0 not exported) to " + exportFile);
            } catch (IOException e) {
                printSplitMeasurements(st, result, System.nanoTime() - exportStart, true);
                System.out.println("FAILED:     cannot write " + exportFile + ": " + e);
                return 1;
            }
            exportNanos = System.nanoTime() - exportStart;
        }
        printSplitMeasurements(st, result, exportNanos, false);
        return 0;
    }

    /**
     * The measurement block of a split run, with the keys of {@link #printMeasurements} in the same
     * order. Counts are the coordinator's merge; {@code explore_ms} is the coordinator's wall time
     * from the root's start to the last committed item; {@code stop_reason} is {@code exhausted} or
     * {@code error} (a split run is exhaustive; an aborted run or a failed export is an error);
     * {@code peak_rss_kb} is the coordinator JVM's own peak, not the workers'. The per-phase times and
     * graph-shape counters of the worker JVMs are not aggregated: those keys are printed empty
     * (applies, not measured).
     */
    private static void printSplitMeasurements(Measured st, SplitCoordinator.Result r, long exportNanos,
                                               boolean exportFailed) {
        st.printed = true;
        long exploreNanos = r.exploreNanos();
        boolean merged = r.failure() == null;
        boolean ok = merged && !exportFailed;
        System.out.println(MEASUREMENTS_MARKER);
        System.out.println("wall_ms: " + (System.nanoTime() - st.wallStartNanos) / 1_000_000);
        System.out.println("explore_ms: " + exploreNanos / 1_000_000);
        System.out.println("summarise_ms: ");
        System.out.println("index_ms: 0");
        System.out.println("lookup_ms: 0");
        System.out.println("export_ms: " + exportNanos / 1_000_000);
        System.out.println("graphs_complete: " + (merged ? r.distinctComplete() : ""));
        System.out.println("graphs_blocked: " + (merged ? r.distinctBlocked() : ""));
        System.out.println("summaries_distinct: "
                + (merged && r.summaryLines() != null ? r.summaryLines().size() : 0));
        System.out.println("iterations: " + (merged ? r.ran() : ""));
        System.out.println("stop_reason: " + (ok ? "exhausted" : "error"));
        long rss = peakRssKb();
        System.out.println("peak_rss_kb: " + (rss < 0 ? "" : Long.toString(rss)));
        System.out.println("max_message_events: ");
        System.out.println("max_porf_pairs: ");
        System.out.println("max_graph_events: ");
        System.out.println("mean_graph_events: ");
        System.out.println("max_visible_events: ");
        System.out.println("mean_visible_events: ");
        System.out.println("max_summary_bytes: ");
        System.out.println("max_porf_count: ");
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
                        + " [seed] [reportPath] [exportFile] [timeoutSeconds] [--spec <file> [--stop-at-first]]"
                        + " [--split W [--k K]] [--worker <run dir> <id>]");
        return 2;
    }
}
