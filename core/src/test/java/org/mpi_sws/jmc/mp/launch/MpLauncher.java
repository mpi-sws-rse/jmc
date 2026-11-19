package org.mpi_sws.jmc.mp.launch;

import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.summary.RunIdentity;
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
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
 *      [--spec &lt;file&gt; [--stop-at-first] [--witness &lt;file or dir/&gt;]]
 *      [--graphs &lt;store&gt; [--graphs-limit N]]
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
 * {@code complete}, {@code visible} must equal the program's visible roles and {@code model} its model;
 * a minor-1 header's {@code roles} must equal {@link MpProgram#headerRoles()}, a minor-0 header is read
 * with a warning on stderr that its roles were not compared) into a
 * {@link SetIndex}, and the {@link MustStrategy} queries it at each completed graph. By default (full
 * mode) the exploration continues past an uncovered summary, keeping the first one and counting every
 * uncovered graph; with {@code --stop-at-first} (bug-finder mode) it stops at the first. The program
 * must implement {@link MpProgram}; {@code exportFile} may still be given (the implementation's
 * summaries are written as in export mode, with {@code first_miss_iteration}, {@code
 * first_miss_wall_ms} and {@code uncovered_total} in the header) or be {@code -}. Printed:
 * {@code spec:}
 * (the file, its program, size, model and tool, lines read, distinct summaries held, duplicates; the
 * model was compared at load: a differing model is refused),
 * {@code lookups:}, {@code verdict:} — {@code conforms} (stack exhausted, every summary covered),
 * {@code reports} (something uncovered) or {@code partial} (the run ended on its bound, a timeout or a
 * failure before anything uncovered) — {@code mode:}, {@code first miss: iteration I, T ms} (on a
 * miss), {@code uncovered: U graphs (D distinct summaries)}, and on a miss {@code witness:} (the first
 * uncovered summary as a format line) and {@code at:} (its iteration and the index's answer). The
 * verdict is a result: exit {@code 0} for all three.
 *
 * <p><b>Witnesses</b>: {@code --witness <path>}, anywhere in the argument list, with {@code --spec} (full or bug-finder
 * mode) writes the uncovered executions as text (the witness format of plan/jmc-must/SUMMARY-FORMAT.md, {@link
 * org.mpi_sws.jmc.mp.summary.WitnessWriter}). A file path is written at the first uncovered execution and never again;
 * a directory (an existing one, or a path ending in {@code /}) gets one {@code witness-<n>.txt} per distinct uncovered
 * summary, {@code n} in order of first occurrence. An existing file, a directory already holding witness files, and an
 * {@code exportFile} that is the witness file or lies in the witness directory are refused; each witness file is
 * created exclusively, and a witness that cannot be written fails the run (exit {@code 1}) without changing the online
 * check's results. Printed: {@code witnesses: W written to <path>}. Refused with {@code --split} (a worker's iterations
 * are not the run's): run the online check without it to write witnesses. Without {@code --witness} nothing changes;
 * with it the writing is inside the exploration's time, so a witness run is never a measurement.
 *
 * <p><b>Graph store</b>: {@code --graphs <store>}, anywhere in the argument list, with strategy {@code must} and an
 * export file, writes the graph of each distinct exported summary at its first occurrence (witness format 1, JSON
 * Lines; {@link org.mpi_sws.jmc.mp.summary.GraphJson}), at most {@code N} with {@code --graphs-limit N} ({@code 0}: the
 * identity line only). Line 1 is {@code {"witness_store":1,"export":H,"run":R}}, then one graph line per summary,
 * {@code {"witness":1,"line":L,"export":H,"run":R,"join":null,"nearest":null,"threads":T}}: {@code H} is the export's
 * header without the online fields, {@code L} the summary line without {@code n}, {@code R} the run's identity
 * ({@link org.mpi_sws.jmc.mp.summary.RunIdentity}: the JMC commit the build recorded, the program and its
 * arguments, and the source identity copied from the {@code SOURCE.md5} manifest at the root of the program's
 * classpath entry, or {@code "unknown"}). The graphs are kept in {@code <store>.part} until the export is written.
 * Printed: {@code graph_store: written=W refused=R ms=T}, and the measurement keys {@code graphs_written},
 * {@code graphs_refused}, {@code graphs_ms} after the shared ones. An existing store is refused. Under {@code --split}
 * the root and every item write their own part in the run directory and the coordinator merges them in the
 * sequential order (the root, then the items {@code N − 1} … {@code 0}), keeping the first graph per summary with
 * its sequential iteration number, so the store equals the sequential run's; {@code graphs_ms} and {@code ms=} are
 * then empty (the walking is spread over the workers). Without {@code --graphs} nothing changes.
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
            if (st.graphs != null && !st.graphs.finished()) {
                // Any end without a written store (a failed run, a failed export, a refused argument): the store's
                // own files go, so that the same store path can be used again.
                st.graphs.abandon();
            }
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
        /** Whether the split run summarises (the program's roles were given; the per-summary counters apply). */
        boolean summarises;
        JmcModelCheckerReport report;
        String failure;
        long checkNanos;
        long indexNanos;
        long exportNanos;
        boolean printed;
        /** The graph store ({@code --graphs}), or {@code null}: its keys follow the shared ones. */
        GraphStore graphs;

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
        String witnessPath = null;
        Path graphsPath = null;
        Long graphsLimit = null;
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
            } else if (rawArgs[i].equals("--graphs")) {
                if (i + 1 >= rawArgs.length) {
                    return usage("--graphs needs a file");
                }
                if (graphsPath != null) {
                    return usage("--graphs given twice");
                }
                graphsPath = Path.of(rawArgs[++i]);
            } else if (rawArgs[i].equals("--graphs-limit")) {
                if (i + 1 >= rawArgs.length) {
                    return usage("--graphs-limit needs a number");
                }
                if (graphsLimit != null) {
                    return usage("--graphs-limit given twice");
                }
                try {
                    graphsLimit = Long.parseLong(rawArgs[++i]);
                } catch (NumberFormatException e) {
                    return usage("--graphs-limit needs a number, not " + rawArgs[i]);
                }
                if (graphsLimit < 0) {
                    return usage("--graphs-limit must be at least 0, was " + graphsLimit);
                }
            } else if (rawArgs[i].equals("--witness")) {
                if (i + 1 >= rawArgs.length) {
                    return usage("--witness needs a file or a directory");
                }
                if (witnessPath != null) {
                    return usage("--witness given twice");
                }
                witnessPath = rawArgs[++i];
            } else {
                positional.add(rawArgs[i]);
            }
        }
        String[] args = positional.toArray(new String[0]);
        if (stopAtFirst && specFile == null) {
            return usage("--stop-at-first needs --spec");
        }
        if (witnessPath != null && specFile == null) {
            return usage("--witness needs --spec");
        }
        if (graphsLimit != null && graphsPath == null) {
            return usage("--graphs-limit needs --graphs");
        }
        if (graphsPath != null && workerDir != null) {
            return usage("--graphs is not a worker's option (a split run's workers write theirs as run.json says)");
        }
        if (witnessPath != null && (splitWorkers != null || splitK != null || workerDir != null)) {
            return usage("--witness cannot be combined with --split: a worker's iterations are not the run's;"
                    + " run the online check without --split to write witnesses");
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
            return constructionFailure(args[0], why);
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
                recorder = new MpRecordingStrategy(seed, reportPath, mp.declaredRoles(), mp.visibleRoles(),
                        mp.payloadNameableRoles());
            } else if (must) {
                mustStrategy = new MustStrategy(seed, reportPath, mp.declaredRoles(), mp.visibleRoles(),
                        mp.payloadNameableRoles());
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
                mustStrategy = new MustStrategy(seed, reportPath, mp.declaredRoles(), mp.visibleRoles(),
                        mp.payloadNameableRoles());
                st.must = mustStrategy;
            }
            long indexStartNanos = System.nanoTime();
            try {
                spec = SpecificationFile.load(specFile, mp.expected());
            } catch (IllegalArgumentException | IOException e) {
                st.failure = "specification " + specFile + " refused: " + e.getMessage();
                return usage(st.failure);
            } finally {
                st.indexNanos = System.nanoTime() - indexStartNanos;
            }
            if (!spec.rolesCompared()) {
                // A minor-0 specification (no roles key) is read without comparing roles, with a warning.
                System.err.println("MpLauncher: warning: specification " + specFile + " has no roles (format"
                        + " minor 0); its roles were not compared with the program's " + mp.headerRoles());
            }
            mustStrategy.checkAgainst(spec.index(), stopAtFirst);
        }
        WitnessFiles witnesses = null;
        if (witnessPath != null) {
            try {
                witnesses = WitnessFiles.open(witnessPath, exportFile);
            } catch (IllegalArgumentException | IOException e) {
                return usage("--witness " + witnessPath + " refused: " + e.getMessage());
            }
            mustStrategy.writeWitnesses(witnesses);
        }
        GraphStore graphs = null;
        if (graphsPath != null) {
            if (exportFile == null || !must) {
                return usage("--graphs needs strategy must and an export file");
            }
            List<String> warnings = new ArrayList<>();
            int argsAt = args[0].indexOf(':');
            RunIdentity identity = RunIdentity.of(programClass, argsAt < 0 ? null : args[0].substring(argsAt + 1),
                    spec == null ? "export" : "online", warnings);
            try {
                graphs = GraphStore.open(graphsPath, exportFile, graphsLimit == null ? Long.MAX_VALUE : graphsLimit,
                        identity);
            } catch (IllegalArgumentException | IOException e) {
                return usage("--graphs " + graphsPath + " refused: " + e.getMessage());
            }
            for (String w : warnings) {
                System.err.println("graphs: " + w);
            }
            mustStrategy.writeGraphs(graphs);
            st.graphs = graphs;
        }

        if (splitWorkers != null || splitK != null || workerDir != null) {
            // The split run prints its own measurement block (the coordinator; a worker prints none).
            st.must = null;
            if (!must) {
                return usage("--split, --k and --worker need strategy must, not " + strategy);
            }
            if (stopAtFirst) {
                return usage("--stop-at-first cannot be combined with --split (the online check runs in full mode"
                        + " under --split)");
            }
            if (iterations != 0) {
                return usage("split mode is exhaustive: iterations must be 0, was " + iterations);
            }
            if (splitK != null && splitWorkers == null) {
                return usage("--k needs --split");
            }
            if (workerDir != null) {
                // The worker summarises when run.json says so (an export or an online check), with the
                // program's roles; it loads the specification named in run.json, never one of its own.
                MpProgram wp = program instanceof MpProgram m ? m : null;
                return SplitWorker.run(workerDir, workerId, program, wp == null ? null : wp.declaredRoles(),
                        wp == null ? null : wp.visibleRoles(), wp == null ? null : wp.payloadNameableRoles(),
                        wp == null ? null : SummaryExporter.modelName(wp.model()));
            }
            st.summarises = mpProgram != null;
            return runSplit(st, args, rawArgs, program, programClass, mpProgram, splitWorkers,
                    splitK == null ? 8 * splitWorkers : splitK, seed, reportPath, exportFile, timeoutSeconds, spec,
                    graphs);
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
            // The model was compared at load (a differing one is refused); it is printed here.
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
                    mpProgram.programName(), mpProgram.size(), mpProgram.visibleRoles(), mpProgram.headerRoles(),
                    mpProgram.model(), enumeration, seed, ran, online));
            boolean exportWritten = false;
            List<String> body = new ArrayList<>(summaries.size());
            for (Map.Entry<Summary, Integer> e : summaries.entrySet()) {
                body.add(e.getKey().toJson(e.getValue()));
            }
            SummaryExporter.sortLines(body);
            lines.addAll(body);
            try {
                if (exportFile.getParent() != null) {
                    Files.createDirectories(exportFile.getParent());
                }
                long exportStartNanos = System.nanoTime();
                try {
                    Files.write(exportFile, lines, StandardCharsets.UTF_8);
                    exportWritten = true;
                } finally {
                    st.exportNanos = System.nanoTime() - exportStartNanos;
                }
                System.out.println("exported:   " + exported + " iterations as "
                        + summaries.size() + " distinct summaries (" + enumeration + ", "
                        + skipped + " not exported) to " + exportFile);
            } catch (IOException e) {
                failure = (failure == null ? "" : failure + "; ") + "cannot write " + exportFile + ": " + e;
            }
            if (graphs != null && exportWritten) {
                // The store carries the export's header without the online fields.
                String storeHeader = SummaryExporter.header(
                        mpProgram.programName(), mpProgram.size(), mpProgram.visibleRoles(), mpProgram.headerRoles(),
                        mpProgram.model(), enumeration, seed, ran, null);
                try {
                    graphs.finish(storeHeader, body);
                    System.out.println("graph_store: written=" + graphs.written() + " refused=" + graphs.refusedCount()
                            + " ms=" + (mustStrategy.graphNanos() + graphs.finishNanos()) / 1_000_000);
                } catch (IOException e) {
                    failure = (failure == null ? "" : failure + "; ") + "cannot write " + graphsPath + ": " + e;
                }
            }
        }
        if (witnesses != null) {
            System.out.println("witnesses:  " + witnesses.written() + " written to " + witnessPath);
            if (witnesses.error() != null) {
                failure = (failure == null ? "" : failure + "; ") + witnesses.error();
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
     * The {@code --graphs} store (witness format 1): the graph of each distinct exported summary at
     * its first occurrence, up to the limit. The graphs are kept in {@code <path>.part} while the run goes on,
     * since every store line carries the export's header, which is known only at the end; {@link #finish}
     * then writes the store: line 1 {@code {"witness_store":1,"export":H,"run":R}} with {@code iteration} null,
     * then one graph line per kept summary, in first-occurrence order:
     * {@code {"witness":1,"line":L,"export":H,"run":R,"join":null,"nearest":null,"threads":T}}. The store and
     * its part file must not exist, and neither may be the export file. A summary whose graph cannot be
     * written is reported on stderr with its line number in the export file
     * ({@code graphs: refused <line>: <reason>}) and counted.
     */
    private static final class GraphStore implements MustStrategy.GraphSink {
        private final Path path;
        private final Path part;
        private final java.io.BufferedWriter out;
        private final long limit;
        private final RunIdentity identity;
        private long written;
        private final List<String[]> refused = new ArrayList<>();
        private IOException error;
        private long finishNanos;
        private boolean finished;
        /** Whether this run created the assembly file (only then does {@link #abandon} remove it). */
        private boolean tmpOwned;

        private GraphStore(Path path, Path part, java.io.BufferedWriter out, long limit, RunIdentity identity) {
            this.path = path;
            this.part = part;
            this.out = out;
            this.limit = limit;
            this.identity = identity;
        }

        static GraphStore open(Path path, Path exportFile, long limit, RunIdentity identity) throws IOException {
            Path part = Path.of(path + ".part");
            Path export = resolved(exportFile);
            for (Path reserved : List.of(path, part, Path.of(path + ".tmp"))) {
                if (resolved(reserved).equals(export)) {
                    throw new IllegalArgumentException("the export file " + exportFile + " is the store's file "
                            + reserved);
                }
            }
            if (Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("the store already exists");
            }
            if (Files.exists(Path.of(path + ".tmp"), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("the store's assembly file " + path + ".tmp already exists");
            }
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            java.io.BufferedWriter out = Files.newBufferedWriter(part, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new GraphStore(path, part, out, limit, identity);
        }

        /**
         * Where writing to {@code p} would land: its components resolved in the file system's order, each symbolic
         * link (also a dangling one, and one in an ancestor) replaced by its target before any later {@code ..} is
         * applied, and names that do not exist yet kept as they are; so two spellings of one file, now or once
         * directories are created, compare equal.
         */
        private static Path resolved(Path p) throws IOException {
            Path abs = p.toAbsolutePath();
            java.util.Deque<String> todo = new java.util.ArrayDeque<>();
            for (Path c : abs) {
                todo.addLast(c.toString());
            }
            Path root = abs.getRoot();
            Path cur = root;
            int hops = 0;
            while (!todo.isEmpty()) {
                String c = todo.pollFirst();
                if (c.isEmpty() || c.equals(".")) {
                    continue;
                }
                if (c.equals("..")) {
                    cur = cur.getParent() == null ? root : cur.getParent();
                    continue;
                }
                Path next = cur.resolve(c);
                if (Files.isSymbolicLink(next)) {
                    if (++hops > 40) {
                        throw new IOException("too many levels of symbolic links at " + p);
                    }
                    Path target = Files.readSymbolicLink(next);
                    java.util.List<String> parts = new java.util.ArrayList<>();
                    for (Path t : target) {
                        parts.add(t.toString());
                    }
                    for (int k = parts.size() - 1; k >= 0; k--) {
                        todo.addFirst(parts.get(k));
                    }
                    if (target.isAbsolute()) {
                        cur = target.getRoot();
                    }
                    continue;
                }
                cur = next;
            }
            return cur;
        }

        @Override
        public boolean wants() {
            return written < limit && error == null;
        }

        @Override
        public void graph(int iteration, String line, String threads) {
            try {
                // One record per line; neither field holds a raw tab (JSON escapes it in strings).
                out.write(Integer.toString(iteration));
                out.write('\t');
                out.write(line);
                out.write('\t');
                out.write(threads);
                out.write('\n');
                written++;
            } catch (IOException e) {
                error = e;
            }
        }

        @Override
        public void refused(String line, String why) {
            refused.add(new String[] {line, why});
        }

        long written() {
            return written;
        }

        long limit() {
            return limit;
        }

        Path path() {
            return path;
        }

        /** Creates the assembly file exclusively, and records that this run owns it. */
        private java.io.BufferedWriter createdBy(Path tmp) throws IOException {
            java.io.BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            tmpOwned = true;
            return w;
        }

        /** Whether {@link #finish} wrote the store. */
        boolean finished() {
            return finished;
        }

        /** Gives the store up: the part and assembly files are closed and removed, nothing is written. */
        void abandon() {
            try {
                out.close();
            } catch (IOException e) {
                // Nothing was promised: the run has failed already.
            }
            try {
                Files.deleteIfExists(part);
                if (tmpOwned) {
                    Files.deleteIfExists(Path.of(path + ".tmp"));
                }
            } catch (IOException e) {
                System.err.println("graphs: cannot remove the store's part files: " + e);
            }
        }

        int refusedCount() {
            return refused.size();
        }

        /**
         * Writes the store from the part file and removes the part file.
         *
         * @param header the export's header without the online fields
         * @param body the export's summary lines, in the order written (for the refusals' line numbers)
         */
        void finish(String header, List<String> body) throws IOException {
            long start = System.nanoTime();
            try {
                assemble(header, body);
            } finally {
                finishNanos = System.nanoTime() - start;
            }
        }

        /** The time {@link #finish} took, in nanoseconds. */
        long finishNanos() {
            return finishNanos;
        }

        private void assemble(String header, List<String> body) throws IOException {
            out.close();
            if (error != null) {
                throw error;
            }
            Map<String, Integer> lineNo = new HashMap<>();
            for (int i = 0; i < body.size(); i++) {
                String l = body.get(i);
                lineNo.put(l.substring(0, l.lastIndexOf(",\"n\":")) + "}", i + 2);
            }
            for (String[] r : refused) {
                Integer n = lineNo.get(r[0]);
                System.err.println("graphs: refused " + (n == null ? "?" : n.toString()) + ": " + r[1]);
            }
            Path tmp = Path.of(path + ".tmp");
            try (java.io.BufferedReader in = Files.newBufferedReader(part, StandardCharsets.UTF_8);
                    java.io.BufferedWriter w = createdBy(tmp)) {
                w.write("{\"witness_store\":1,\"export\":" + header + ",\"run\":" + identity.toJson(null) + "}\n");
                String rec;
                while ((rec = in.readLine()) != null) {
                    int a = rec.indexOf('\t');
                    int b = rec.indexOf('\t', a + 1);
                    StringBuilder sb = new StringBuilder("{\"witness\":1,\"line\":");
                    org.mpi_sws.jmc.mp.summary.MpValueEncoder.quote(sb, rec.substring(a + 1, b));
                    sb.append(",\"export\":").append(header).append(",\"run\":")
                            .append(identity.toJson(Integer.parseInt(rec.substring(0, a))))
                            .append(",\"join\":null,\"nearest\":null,\"threads\":").append(rec.substring(b + 1))
                            .append("}\n");
                    w.write(sb.toString());
                }
            }
            Files.move(tmp, path);
            Files.delete(part);
            finished = true;
        }
    }

    /**
     * The {@code --witness} target: a file, written at the first uncovered execution and never again, or a directory
     * (an existing one, or a path ending in {@code /}, created), with one {@code witness-<n>.txt} per distinct
     * uncovered summary, {@code n} from 1 in order of first occurrence. A file that exists, or a directory that already
     * holds a {@code witness-*.txt}, is refused, so that no witness of an earlier run is taken for this one's, and so
     * is an export file that is the witness file or lies in the witness directory, compared with symbolic links
     * resolved. Each file is created exclusively ({@code CREATE_NEW}), so a file that appeared meanwhile is never
     * overwritten. A write that fails, a file that exists at the write, or an execution that cannot be written is
     * recorded, ends the writing and fails the run.
     */
    private static final class WitnessFiles implements MustStrategy.WitnessSink {
        private final Path file;
        private final Path dir;
        private int written;
        private String error;

        private WitnessFiles(Path file, Path dir) {
            this.file = file;
            this.dir = dir;
        }

        static WitnessFiles open(String path, Path exportFile) throws IOException {
            Path p = Path.of(path);
            Path export = exportFile == null ? null : canonical(exportFile);
            if (Files.isDirectory(p) || path.endsWith("/")) {
                if (export != null && canonical(p).equals(export.getParent())) {
                    throw new IllegalArgumentException(
                            "the export file " + exportFile + " lies in the witness directory");
                }
                Files.createDirectories(p);
                try (var listing = Files.newDirectoryStream(p, "witness-*.txt")) {
                    if (listing.iterator().hasNext()) {
                        throw new IllegalArgumentException("the directory already holds witness files");
                    }
                }
                return new WitnessFiles(null, p);
            }
            if (Files.exists(p)) {
                throw new IllegalArgumentException("the file exists");
            }
            if (export != null && canonical(p).equals(export)) {
                throw new IllegalArgumentException("it is the export file");
            }
            Path parent = p.toAbsolutePath().getParent();
            if (parent == null || !Files.isDirectory(parent)) {
                throw new IllegalArgumentException("its directory does not exist");
            }
            return new WitnessFiles(p, null);
        }

        /**
         * The path as the file system resolves it, also where it does not exist yet ({@code realpath -m}):
         * component by component from the root, a symbolic link (dangling or not) is replaced by its
         * target, resolved against the link's directory, and {@code ..} is applied only to the resolved
         * prefix, so that two spellings of one file compare equal before either exists. More than 40
         * links (a loop) is an error.
         */
        private static Path canonical(Path path) throws IOException {
            return canonical(path, 0);
        }

        private static Path canonical(Path path, int links) throws IOException {
            Path abs = path.toAbsolutePath();
            Path current = abs.getRoot();
            for (int i = 0; i < abs.getNameCount(); i++) {
                String name = abs.getName(i).toString();
                if (name.equals(".")) {
                    continue;
                }
                if (name.equals("..")) {
                    // current is resolved, so its parent is the directory ".." names.
                    current = current.getParent() == null ? current : current.getParent();
                    continue;
                }
                Path next = current.resolve(name);
                if (Files.isSymbolicLink(next)) {
                    if (links >= 40) {
                        throw new IOException("too many symbolic links at " + next);
                    }
                    Path target = current.resolve(Files.readSymbolicLink(next));
                    for (int j = i + 1; j < abs.getNameCount(); j++) {
                        target = target.resolve(abs.getName(j).toString());
                    }
                    return canonical(target, links + 1);
                }
                current = next;
            }
            return current;
        }

        @Override
        public void uncovered(int iteration, String summaryLine, String witness) {
            if (error != null || (file != null && written > 0)) {
                return;
            }
            Path target = file != null ? file : dir.resolve("witness-" + (written + 1) + ".txt");
            try {
                Files.writeString(target, witness, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                written++;
            } catch (IOException e) {
                error = "cannot write witness " + target + ": " + e;
            }
        }

        @Override
        public void refused(int iteration, String summaryLine, String why) {
            if (error == null && !(file != null && written > 0)) {
                error = "cannot write the witness of iteration " + iteration + ": " + why;
            }
        }

        int written() {
            return written;
        }

        String error() {
            return error;
        }
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
     *       {@code max_message_events}: send and receive events of all threads in the largest summarised
     *       graph (empty when no graph was summarised, as in the counting mode);
     *       {@code max_porf_pairs}: the most non-zero order-count entries in one summary;</li>
     *   <li>with a specification file, {@code first_miss_iteration} (1-based, 0 for none),
     *       {@code first_miss_wall_ms} (since the start of the exploration, 0 for none) and
     *       {@code uncovered_total};</li>
     *   <li>the graph-shape counters over the summarised graphs (the first four empty when no
     *       graph was summarised): {@code max_graph_events} and {@code mean_graph_events} (the event lines
     *       of the graph's witness: BEGIN, TCREATE, SEND, RECV, NONDET, TJOIN, BLK, ERROR, END), {@code
     *       max_visible_events} and {@code mean_visible_events} (the events of the visible words, per
     *       summarised graph), {@code max_summary_bytes}
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
        // The graph, message and visible event counters are per summarised graph; empty when none was.
        boolean summarised = x.summarisedGraphs() > 0;
        System.out.println("max_message_events: " + (summarised ? Integer.toString(x.maxMessageEvents()) : ""));
        System.out.println("max_porf_pairs: " + x.maxPorfPairs());
        if (st.online) {
            System.out.println("first_miss_iteration: " + m.uncoveredIteration());
            System.out.println("first_miss_wall_ms: " + Math.max(0, x.firstMissWallMs()));
            System.out.println("uncovered_total: " + m.uncoveredTotal());
        }
        System.out.println("max_graph_events: " + (summarised ? Integer.toString(x.maxGraphEvents()) : ""));
        System.out.println("mean_graph_events: "
                + (summarised ? String.format(Locale.ROOT, "%.3f", x.meanGraphEvents()) : ""));
        System.out.println("max_visible_events: " + (summarised ? Integer.toString(x.maxVisibleEvents()) : ""));
        System.out.println("mean_visible_events: "
                + (summarised ? String.format(Locale.ROOT, "%.3f", x.meanVisibleEvents()) : ""));
        System.out.println("max_summary_bytes: " + x.maxSummaryBytes());
        System.out.println("max_porf_count: " + x.maxPorfCount());
        if (st.graphs != null) {
            System.out.println("graphs_written: " + st.graphs.written());
            System.out.println("graphs_refused: " + st.graphs.refusedCount());
            System.out.println("graphs_ms: " + (m.graphNanos() + st.graphs.finishNanos()) / 1_000_000);
        }
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
     * {@code min(W, N)} worker JVMs (this launcher with {@code --worker}, the same class path and JVM options),
     * merges, and prints and exports as a sequential {@code must} run does. The run directory is
     * {@code <reportPath>/split-<random id>}.
     *
     * @return {@code 0} for a complete run, {@code 1} if it aborted (incomplete; nothing is exported)
     */
    private static int runSplit(Measured st, String[] args, String[] rawArgs, Runnable program,
                                Class<?> programClass,
                                MpProgram mpProgram, int workers, int k, long seed, String reportPath,
                                Path exportFile, long timeoutSeconds, SpecificationFile.Loaded spec,
                                GraphStore graphs) {
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
        if (spec != null) {
            System.out.println("spec:       " + spec.path() + " (program " + spec.program() + ", size "
                    + spec.size() + ", model " + spec.model() + ", tool " + spec.tool() + "; " + spec.lines()
                    + " lines, " + spec.index().size() + " distinct summaries held, " + spec.duplicates()
                    + " duplicates)");
        }
        long startNanos = System.nanoTime();
        SplitCoordinator.Result result;
        try {
            result = SplitCoordinator.run(new SplitCoordinator.Config(identity, program,
                    mpProgram == null ? null : mpProgram.declaredRoles(),
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
                    }, false, Process::isAlive, spec, mpProgram == null ? null : mpProgram.payloadNameableRoles(),
                    graphs == null ? null : graphs.limit()));
        } catch (IOException e) {
            System.out.println("FAILED:     cannot prepare " + runDir + ": " + e);
            return 1;
        }
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1e9;
        System.out.println("split:      K " + k + ", W " + workers + ", R " + result.r() + ", N " + result.n()
                + (result.split() ? "" : " (no split: the root exhausted the stack)") + "; run directory " + runDir);
        if (result.failure() != null) {
            if (graphs != null) {
                // No export, so no store; the run directory keeps the parts.
                graphs.abandon();
            }
            System.out.printf(Locale.ROOT, "elapsed:    %.3f s%n", elapsedSeconds);
            printSplitOnline(result);
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
        printSplitOnline(result);
        long exportNanos = 0;
        if (exportFile != null) {
            List<String> lines = new ArrayList<>();
            SplitCoordinator.Online on = result.online();
            lines.add(SummaryExporter.header(mpProgram.programName(), mpProgram.size(), mpProgram.visibleRoles(),
                    mpProgram.headerRoles(), mpProgram.model(), "complete", seed, result.ran(), on == null ? null
                            : new SummaryExporter.OnlineFields(on.firstMissIteration(),
                                    Math.max(0, on.firstMissWallMs()), on.uncoveredTotal())));
            List<String> body = new ArrayList<>(result.summaryLines());
            SummaryExporter.sortLines(body);
            lines.addAll(body);
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
            if (graphs != null) {
                String storeHeader = SummaryExporter.header(mpProgram.programName(), mpProgram.size(),
                        mpProgram.visibleRoles(), mpProgram.headerRoles(), mpProgram.model(), "complete", seed,
                        result.ran(), null);
                try {
                    mergeGraphs(graphs, runDir, result);
                    graphs.finish(storeHeader, body);
                    // The walking is spread over the workers: the time is not combined (empty, not zero).
                    System.out.println("graph_store: written=" + graphs.written() + " refused=" + graphs.refusedCount()
                            + " ms=");
                } catch (IOException | RuntimeException e) {
                    printSplitMeasurements(st, result, exportNanos, true);
                    System.out.println("FAILED:     cannot write the graph store " + graphs.path() + ": " + e);
                    return 1;
                }
            }
        }
        printSplitMeasurements(st, result, exportNanos, false);
        return 0;
    }

    /**
     * Feeds the graph store from the split run's parts in the sequential order: the root's part, then the items
     * {@code N − 1} … {@code 0}, each the part of its committed attempt, with the offsets that make their iterations
     * sequential: 0 for the root, then {@code R} plus the iterations of the items before. Without a split the
     * root's part is the whole run.
     */
    private static void mergeGraphs(GraphStore graphs, Path runDir, SplitCoordinator.Result result)
            throws IOException {
        List<Path> parts = new ArrayList<>(List.of(runDir.resolve(SplitCoordinator.ROOT_GRAPHS)));
        List<Integer> offsets = new ArrayList<>(List.of(0));
        if (result.split()) {
            int offset = result.r();
            for (int j = result.n() - 1; j >= 0; j--) {
                int attempt = SplitCoordinator.readJson(runDir.resolve("item-" + j + ".json")).get("attempt").getAsInt();
                parts.add(runDir.resolve(SplitCoordinator.itemGraphs(j, attempt)));
                offsets.add(offset);
                offset += result.itemIterations().get(j);
            }
        }
        org.mpi_sws.jmc.strategies.must.split.GraphPart.merge(graphs, parts, offsets);
    }

    /**
     * The measurement block of a split run, with the keys of {@link #printMeasurements} in the same
     * order. Only what combines exactly is combined: counts, summaries and iterations from the
     * merge; {@code explore_ms}, the coordinator's wall time from the root's start to the last
     * committed item; {@code stop_reason} {@code exhausted} or {@code error} (a split run is
     * exhaustive; an aborted run or a failed export is an error); {@code peak_rss_kb}, the largest
     * peak over the coordinator and every worker process; the graph-shape maxima, the maximum over the
     * root and every worker process. The phase times ({@code summarise_ms}) and the two means do not
     * combine (worker phases interleave) and are printed empty (applies, not measured).
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
        // Phase times of the online check do not combine across processes: empty under an online check.
        System.out.println("index_ms: " + (r.online() == null ? "0" : ""));
        System.out.println("lookup_ms: " + (r.online() == null ? "0" : ""));
        System.out.println("export_ms: " + exportNanos / 1_000_000);
        System.out.println("graphs_complete: " + (merged ? r.distinctComplete() : ""));
        System.out.println("graphs_blocked: " + (merged ? r.distinctBlocked() : ""));
        System.out.println("summaries_distinct: "
                + (merged && r.summaryLines() != null ? r.summaryLines().size() : 0));
        System.out.println("iterations: " + (merged ? r.ran() : ""));
        System.out.println("stop_reason: " + (ok ? "exhausted" : "error"));
        long rss = Math.max(peakRssKb(), r.workerPeakRssKb());
        System.out.println("peak_rss_kb: " + (rss < 0 ? "" : Long.toString(rss)));
        // Per summarised graph; a split run summarises only with the program's roles.
        boolean summarised = st.summarises;
        System.out.println("max_message_events: " + (summarised ? maximum(r, "max_message_events") : ""));
        System.out.println("max_porf_pairs: " + maximum(r, "max_porf_pairs"));
        if (r.online() != null) {
            SplitCoordinator.Online on = r.online();
            System.out.println("first_miss_iteration: " + (on.firstMissIteration() < 0 ? "" : on.firstMissIteration()));
            System.out.println("first_miss_wall_ms: " + Math.max(0, on.firstMissWallMs()));
            System.out.println("uncovered_total: " + (on.uncoveredTotal() == null ? "" : on.uncoveredTotal()));
        }
        System.out.println("max_graph_events: " + (summarised ? maximum(r, "max_graph_events") : ""));
        System.out.println("mean_graph_events: ");
        System.out.println("max_visible_events: " + (summarised ? maximum(r, "max_visible_events") : ""));
        System.out.println("mean_visible_events: ");
        System.out.println("max_summary_bytes: " + maximum(r, "max_summary_bytes"));
        System.out.println("max_porf_count: " + maximum(r, "max_porf_count"));
        if (st.graphs != null) {
            System.out.println("graphs_written: " + (ok ? Long.toString(st.graphs.written()) : ""));
            System.out.println("graphs_refused: " + (ok ? Integer.toString(st.graphs.refusedCount()) : ""));
            // The walking is spread over the worker processes and does not combine, as summarise_ms.
            System.out.println("graphs_ms: ");
        }
    }

    /**
     * The online check's lines of a split run, as a sequential run prints them; the first miss is the
     * sequential-order one, its time the earliest miss in wall time (both by ruling). After an abort the
     * totals are not printed, and the first miss's iteration only if every segment before it was committed.
     */
    private static void printSplitOnline(SplitCoordinator.Result result) {
        SplitCoordinator.Online on = result.online();
        if (on == null) {
            return;
        }
        String verdict = on.miss() ? "reports" : result.failure() == null ? "conforms" : "partial";
        System.out.println("lookups:    " + on.lookups() + " (" + on.found() + " found, " + on.noSig()
                + " signature misses, " + on.notFound() + " not found)" + (result.failure() == null ? ""
                : " over the committed segments"));
        System.out.println("verdict:    " + verdict);
        System.out.println("mode:       full");
        String iteration = on.firstMissIteration() < 0 ? "unknown (a segment before it is missing)"
                : Integer.toString(on.firstMissIteration());
        if (on.miss()) {
            System.out.println("first miss: iteration " + iteration + " (sequential order), earliest "
                    + on.firstMissWallMs() + " ms (wall)");
        }
        if (on.uncoveredTotal() != null) {
            System.out.println("uncovered:  " + on.uncoveredTotal() + " graphs (" + on.uncoveredDistinct()
                    + " distinct summaries)");
        }
        if (on.miss()) {
            System.out.println("witness:    " + on.witness());
            System.out.println("at:         iteration " + iteration + ", " + (on.answer().equals("NoSig")
                    ? "NoSig (no specification summary has this signature)"
                    : "NotFound (the signature is held, no order is <= the query's)"));
        }
    }

    /** A combined maximum of a split run, empty when it is not available (an aborted run). */
    private static String maximum(SplitCoordinator.Result r, String key) {
        Long v = r.maxima().get(key);
        return v == null ? "" : Long.toString(v);
    }

    /**
     * Prints a usage error and returns the bad-arguments exit status.
     *
     * @param why what was wrong
     * @return {@code 2}
     */
    private static int usage(String why) {
        System.err.println("MpLauncher: " + why);
        return usageLine();
    }

    /**
     * Reports a program that cannot be loaded or constructed: the failure, each of its causes
     * ({@code caused by: ...}, so that a cause wrapped in an {@code ExceptionInInitializerError} is
     * shown), and the stack trace of the innermost cause, then the usage line. The status stays 2:
     * a program that cannot be constructed has not been checked.
     */
    private static int constructionFailure(String program, Throwable why) {
        System.err.println("MpLauncher: cannot load and construct " + program + ": " + why);
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(why);
        Throwable innermost = why;
        for (Throwable c = why.getCause(); c != null && seen.add(c); c = c.getCause()) {
            System.err.println("  caused by: " + c);
            innermost = c;
        }
        innermost.printStackTrace(System.err);
        return usageLine();
    }

    /** Prints the usage line and returns the usage status, 2. */
    private static int usageLine() {
        System.err.println(
                "usage: MpLauncher <program class[:args]> [strategy=mp-random] [iterations=1000]"
                        + " [seed] [reportPath] [exportFile] [timeoutSeconds] [--spec <file> [--stop-at-first]"
                        + " [--witness <file or dir/>]] [--graphs <store> [--graphs-limit N]]"
                        + " [--split W [--k K]] [--worker <run dir> <id>]");
        return 2;
    }
}
