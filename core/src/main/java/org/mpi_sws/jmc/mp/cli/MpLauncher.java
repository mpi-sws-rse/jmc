package org.mpi_sws.jmc.mp.cli;

import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.summary.RunIdentity;
import org.mpi_sws.jmc.mp.summary.SetIndex;
import org.mpi_sws.jmc.mp.summary.SpecificationFile;
import org.mpi_sws.jmc.mp.summary.Summary;
import org.mpi_sws.jmc.mp.summary.SummaryExporter;
import org.mpi_sws.jmc.strategies.SchedulingStrategyFactory;
import org.mpi_sws.jmc.strategies.mp.GraphReplayStrategy;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runs one message-passing program (written against {@code org.mpi_sws.jmc.api.mp}) through one
 * {@link JmcModelChecker#check} in this JVM, without the instrumentation agent, and prints the run's numbers. It counts
 * executions, exports their summaries, checks them online against a specification file, writes witness graphs, and
 * replays one graph.
 *
 * <pre>
 * java [-ea] -cp &lt;core main and test classes, resources, dependencies&gt; org.mpi_sws.jmc.mp.launch.MpLauncher \
 *      &lt;program class[:args]&gt; [strategy] [iterations] [seed] [reportPath] [exportFile] [timeoutSeconds]
 *      [--spec &lt;file&gt; [--stop-at-first] [--witness &lt;file or dir/&gt;]] [--graphs &lt;store&gt; [--graphs-limit N]]
 *      [--split W [--k K]]
 * java ... MpLauncher --replay &lt;graph.json&gt; [--report &lt;r.json&gt;] [--allow-source-change]
 *      [--allow-tool-change] [&lt;program class[:args]&gt;]
 * </pre>
 *
 * <p><b>Program.</b> A {@link Runnable}; {@code Class} uses its public no-argument constructor, {@code Class:a,b,...}
 * its public {@code (String...)} constructor with the comma-separated arguments. Export, the online check, the graph
 * store and replay need an {@link MpProgram}. The program's {@code toString()} is printed after the run.
 *
 * <p><b>Positional arguments.</b> {@code strategy}: {@code mp-random} (default, samples) or {@code must} (explores
 * every distinct execution; {@code P2P} and {@code ASYN} only). {@code iterations}: default {@code 1000}; {@code 0}
 * with {@code must} means unbounded, until the exploration ends or the timeout. {@code seed}: default from the clock,
 * printed. {@code reportPath}: default {@code build/mp-launch/<program simple name>}; the checker deletes the plain
 * files in it at the start of the run, so give each run its own directory. {@code exportFile}: the summary file;
 * {@code -} or absent for none (under {@code must}: counting mode, any {@code Runnable}). {@code timeoutSeconds}:
 * default 600, applied to {@code must}. Named options may stand anywhere after the program.
 *
 * <p><b>Export.</b> With {@code exportFile} the summaries are written in one go at the end: the header line, then one
 * line per distinct summary with its multiplicity, sorted by bytes. The header's {@code enumeration} is
 * {@code complete} when {@code must} exhausted its stack, {@code bounded} when a {@code must} run ended without
 * failure before that (an iteration bound, or {@code --stop-at-first}), {@code aborted} on a failure or the timeout,
 * and {@code sampled} for an {@code mp-random} run that completed.
 *
 * <p><b>Online check</b> ({@code must}). {@code --spec <file>} loads a specification file (its {@code enumeration}
 * must be {@code complete}, its visible roles, roles and model the program's) and looks every completed execution up
 * in it. Printed: {@code spec:}, {@code lookups:}, {@code verdict:} ({@code conforms}: the exploration ended and
 * every summary is covered; {@code reports}: something is uncovered; {@code partial}: nothing uncovered, but the run
 * ended on its bound or the timeout), {@code mode:}, {@code first miss:}, {@code uncovered:}, and on a miss
 * {@code witness:} (the first uncovered summary) and {@code at:}. {@code --stop-at-first} stops at the first
 * uncovered execution. {@code --witness <file>} writes the first uncovered execution as a text witness,
 * {@code --witness <dir>/} one {@code witness-<n>.txt} per distinct uncovered summary; existing witness files are
 * never overwritten.
 *
 * <p><b>Graph store</b> ({@code must} with an export file). {@code --graphs <store>} writes the graph of each distinct
 * exported summary at its first occurrence (JSON Lines; at most {@code N} with {@code --graphs-limit N}): line 1
 * {@code {"witness_store":1,"export":H,"run":R}}, then {@code {"witness":1,"line":L,"export":H,"run":R,"join":null,
 * "nearest":null,"threads":T}}, where {@code H} is the export's header without the online fields, {@code L} the
 * summary line without its multiplicity, {@code R} the run's identity ({@link org.mpi_sws.jmc.mp.summary.RunIdentity}:
 * the JMC commit the build recorded, the program and its arguments, the source identity copied from the
 * {@code SOURCE.md5} manifest at the root of the program's classpath entry, or {@code "unknown"}) and {@code T} the
 * graph ({@link org.mpi_sws.jmc.mp.summary.GraphJson}). Printed: {@code graph_store: written=W refused=R ms=T}. An
 * existing store is refused.
 *
 * <p><b>Replay.</b> {@code --replay <graph.json>} runs one execution forced along the graph (scheduling, received
 * messages, choices) and compares every event with it ({@link org.mpi_sws.jmc.strategies.mp.GraphReplayStrategy}).
 * Printed: {@code replay: reproduced (<E> events, <T> threads)} or {@code replay: deviation at (<t>, <k>) thread
 * "<name>": <kind>: expected <event>, got <event>}; {@code --report} writes the result as JSON. A graph of another
 * tool or program is refused, and so is one from another JMC build or benchmark source unless
 * {@code --allow-tool-change} or {@code --allow-source-change} is given.
 *
 * <p><b>Split runs</b> (advanced). {@code --split W [--k K]} explores with {@code W} worker JVMs once the search
 * stack holds {@code K} entries (default {@code 8·W}); {@code must}, iterations {@code 0}; the export and the graph
 * store equal the sequential run's; {@code --witness} and {@code --stop-at-first} are refused. {@code --worker} is
 * internal: the coordinator starts its workers with it.
 *
 * <p><b>Measurements.</b> A {@code must} run ends with a block opened by {@code --- measurements}: {@code wall_ms},
 * {@code explore_ms}, {@code summarise_ms}, {@code index_ms}, {@code lookup_ms}, {@code export_ms},
 * {@code graphs_complete}, {@code graphs_blocked}, {@code summaries_distinct}, {@code iterations},
 * {@code stop_reason}, {@code peak_rss_kb}, the graph-shape maxima and means, and with {@code --graphs}
 * {@code graphs_written}, {@code graphs_refused}, {@code graphs_ms}. A key whose value cannot be measured is empty.
 *
 * <p><b>Exit status.</b> {@code 0}: the run completed, including every online verdict ({@code conforms},
 * {@code reports}, {@code partial}: read the {@code verdict:} line), a timeout, a stop at the first miss, and
 * {@code replay: reproduced}. {@code 1}: the run failed, after a {@code FAILED:} line: a failed assertion or an
 * exception in any process or in main (also in the last iteration), a fault of the strategy (a model {@code must}
 * does not support, an export shape rule broken), an export, store or witness file that cannot be written, a split
 * run that did not finish; and {@code replay: deviation}. {@code 2}: bad arguments or a refused input: a wrong
 * argument count, an unknown option or strategy, a class that cannot be loaded or constructed, a non-numeric or
 * out-of-range number, a refused specification file, store or witness target, a refused replay.
 */
public final class MpLauncher {

    /** The line that opens the block of measurement lines ({@link #printMeasurements}). */
    public static final String MEASUREMENTS_MARKER = "--- measurements";

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
        if (List.of(rawArgs).contains("--replay")) {
            return runReplay(rawArgs, st);
        }
        // The named options are taken out wherever they stand; the rest is positional.
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
                // The split options, like --spec, may stand anywhere after the program.
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
        // "-" (or no sixth argument) means no export: under must, the counting mode.
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
                                    // daemons run on after main's body (raises nothing without them)
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
     * {@code --replay g.json [--report r.json] [--allow-source-change] [--allow-tool-change] [<class>[:<args>]]}: one
     * execution of the program the graph names, forced along the graph ({@link GraphReplayStrategy}). Refused (exit
     * {@code 2}) before running: bad arguments, any other option ({@code --split} included: a replay is one
     * execution), a graph file that is not one witness graph line of format 1 or breaks the structural rules checked
     * here, a graph of another tool, a program or arguments that differ from the graph's ({@code side},
     * {@code model} and {@code options} must be JMC's), a {@code tool_commit} that differs from this build's or is
     * {@code "unknown"} on either side (unless {@code --allow-tool-change}), a source that differs from the
     * program's {@code SOURCE.md5} manifest or is {@code "unknown"} on either side (unless
     * {@code --allow-source-change}). The projection rule (the graph's {@code line} is its summary) is checked by the
     * renderer's {@code --check}, not here. Printed: {@code replay: reproduced (<E> events, <T> threads)} (exit
     * {@code 0}) or {@code replay: deviation at (<t>, <k>) thread "<name>": <kind>: expected <text>, got <text>}
     * (exit {@code 1}); a run that fails otherwise prints {@code FAILED:} (exit {@code 1}). Then the measurement block
     * with {@code wall_ms}, {@code peak_rss_kb} and {@code replay_events}.
     */
    private static int runReplay(String[] rawArgs, Measured st) {
        String graphPath = null;
        String reportFile = null;
        boolean allowSource = false;
        boolean allowTool = false;
        String explicit = null;
        for (int i = 0; i < rawArgs.length; i++) {
            String a = rawArgs[i];
            switch (a) {
                case "--replay" -> {
                    if (i + 1 >= rawArgs.length || graphPath != null) {
                        return usage("--replay needs one graph file");
                    }
                    graphPath = rawArgs[++i];
                }
                case "--report" -> {
                    if (i + 1 >= rawArgs.length || reportFile != null) {
                        return usage("--report needs one file");
                    }
                    reportFile = rawArgs[++i];
                }
                case "--allow-source-change" -> allowSource = true;
                case "--allow-tool-change" -> allowTool = true;
                default -> {
                    if (a.startsWith("--")) {
                        return usage(a + " cannot be combined with --replay (a replay is one execution)");
                    }
                    if (explicit != null) {
                        return usage("--replay takes at most one program argument, got " + explicit + " and " + a);
                    }
                    explicit = a;
                }
            }
        }
        JsonObject g;
        try {
            List<String> ls = Files.readAllLines(Path.of(graphPath), StandardCharsets.UTF_8);
            if (ls.size() != 1) {
                return usage("--replay " + graphPath + ": a graph file holds exactly one line, found " + ls.size());
            }
            g = JsonParser.parseString(ls.get(0)).getAsJsonObject();
            String problem = graphProblem(g);
            if (problem != null) {
                return usage("--replay " + graphPath + " refused: " + problem);
            }
        } catch (IOException | RuntimeException e) {
            return usage("--replay " + graphPath + " refused: " + e.getMessage());
        }
        JsonObject run = g.getAsJsonObject("run");
        if (!run.get("tool").getAsString().equals("jmc")) {
            return usage("--replay: the graph is " + run.get("tool").getAsString() + "'s; cross-tool replay is not supported");
        }
        if (!run.get("side").isJsonNull() || !run.get("model").isJsonNull()
                || !run.getAsJsonObject("options").entrySet().isEmpty()) {
            return usage("--replay: side, model and options of a JMC graph are null, null and {}");
        }
        String entry = run.get("entry").getAsString();
        JsonArray argList = run.getAsJsonArray("args");
        if (argList.size() > 1) {
            return usage("--replay: a JMC graph has at most one args element");
        }
        String programSpec = entry + (argList.isEmpty() ? "" : ":" + argList.get(0).getAsString());
        if (explicit != null && !explicit.equals(programSpec)) {
            return usage("--replay: the program argument " + explicit + " differs from the graph's " + programSpec);
        }
        Class<?> programClass;
        Runnable program;
        int colon = programSpec.indexOf(':');
        String className = colon < 0 ? programSpec : programSpec.substring(0, colon);
        String[] programArgs = colon < 0 ? null : programSpec.substring(colon + 1).split(",", -1);
        try {
            programClass = Class.forName(className);
            Object instance = programArgs == null
                    ? programClass.getDeclaredConstructor().newInstance()
                    : programClass.getDeclaredConstructor(String[].class).newInstance((Object) programArgs);
            if (!(instance instanceof Runnable r)) {
                return usage(programSpec + " is not a Runnable");
            }
            program = r;
        } catch (ReflectiveOperationException | LinkageError e) {
            Throwable why = e.getCause() != null ? e.getCause() : e;
            return constructionFailure(programSpec, why);
        }
        List<String> warnings = new ArrayList<>();
        // Identity: the graph's against this build's commit and the program's manifest.
        String ours = RunIdentity.buildCommit();
        String theirs = run.get("tool_commit").getAsString();
        if (!ours.equals(theirs) || ours.equals("unknown")) {
            String why = "tool_commit " + theirs + " (this build: " + ours + ")";
            if (!allowTool) {
                return usage("--replay refused: " + why + "; --allow-tool-change accepts it");
            }
            warnings.add("tool-change: " + why);
        }
        List<String> manifestWarnings = new ArrayList<>();
        RunIdentity mine = RunIdentity.of(programClass, argList.isEmpty() ? null : argList.get(0).getAsString(),
                "export", manifestWarnings);
        JsonElement graphSource = run.get("source");
        String mineSource = JsonParser.parseString(mine.toJson(null)).getAsJsonObject().get("source").toString();
        boolean unknown = mine.sourceFiles() == null || graphSource.isJsonPrimitive();
        if (unknown || !JsonParser.parseString(mineSource).equals(graphSource)) {
            String why = unknown ? "source unknown" + (manifestWarnings.isEmpty() ? "" : " (" + manifestWarnings.get(0)
                    + ")") : "source differs from the program's manifest";
            if (!allowSource) {
                return usage("--replay refused: " + why + "; --allow-source-change accepts it");
            }
            warnings.add("source-change: " + why);
        }

        JsonArray threads = g.getAsJsonArray("threads");
        GraphReplayStrategy replay = new GraphReplayStrategy(threads, 1L, "build/test-results/jmc-replay");
        String failure = null;
        try {
            JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                    .strategyType("mp-random")
                    .numIterations(1)
                    .seed(1L)
                    .reportPath("build/test-results/jmc-replay")
                    .strategyConstructor(c -> replay)
                    .build();
            new JmcModelChecker(config).check(new JmcFunctionalTestTarget(programClass.getName(),
                    () -> MpProcess.runMain(program)));
        } catch (JmcCheckerException e) {
            failure = e.getMessage();
        } catch (RuntimeException | Error e) {
            failure = e.toString();
        }
        GraphReplayStrategy.Deviation d = replay.deviation() != null ? replay.deviation()
                : failure == null ? replay.finish() : null;
        warnings.addAll(replay.warnings());
        int status;
        if (d != null) {
            System.out.println("replay: deviation at (" + d.thread() + ", " + d.position() + ") thread \"" + d.name()
                    + "\": " + d.kind() + ": expected " + d.expected() + ", got " + d.actual());
            status = 1;
        } else if (failure != null) {
            System.out.println("FAILED:     " + failure);
            status = 1;
        } else {
            System.out.println("replay: reproduced (" + replay.events() + " events, " + threads.size() + " threads)");
            status = 0;
        }
        for (String w : new java.util.LinkedHashSet<>(warnings)) {
            System.err.println("replay: warning: " + w);
        }
        if (reportFile != null && (d != null || failure == null)) {
            JsonObject r = new JsonObject();
            r.addProperty("replay", 1);
            r.addProperty("graph", graphPath);
            r.addProperty("result", d == null ? "reproduced" : "deviation");
            if (d == null) {
                r.add("at", com.google.gson.JsonNull.INSTANCE);
                r.add("thread", com.google.gson.JsonNull.INSTANCE);
                r.add("kind", com.google.gson.JsonNull.INSTANCE);
                r.add("expected", com.google.gson.JsonNull.INSTANCE);
                r.add("actual", com.google.gson.JsonNull.INSTANCE);
            } else {
                JsonArray at = new JsonArray();
                at.add(d.thread());
                at.add(d.position());
                r.add("at", at);
                if (d.name() == null) {
                    r.add("thread", com.google.gson.JsonNull.INSTANCE);
                } else {
                    r.addProperty("thread", d.name());
                }
                r.addProperty("kind", d.kind());
                r.addProperty("expected", d.expected());
                r.addProperty("actual", d.actual());
            }
            JsonArray ws = new JsonArray();
            new java.util.LinkedHashSet<>(warnings).forEach(ws::add);
            r.add("warnings", ws);
            try {
                Files.writeString(Path.of(reportFile), new com.google.gson.GsonBuilder().serializeNulls()
                        .disableHtmlEscaping().create().toJson(r) + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("FAILED:     cannot write " + reportFile + ": " + e);
                status = 1;
            }
        }
        System.out.println(MEASUREMENTS_MARKER);
        System.out.println("wall_ms: " + (System.nanoTime() - st.wallStartNanos) / 1_000_000);
        long rss = peakRssKb();
        System.out.println("peak_rss_kb: " + (rss < 0 ? "" : Long.toString(rss)));
        System.out.println("replay_events: " + replay.events());
        return status;
    }

    /**
     * The validity rules of a witness graph file (format 1) that do not need the projection, or {@code null} when they
     * hold: the keys and their order at the top level, in {@code run}, in {@code source}, in each thread and in each
     * event (no unknown key), and their types; thread 0 is a non-daemon {@code main}; each visible name occurs once; a
     * thread's events begin with {@code BEGIN}, end with {@code END}, {@code BLK} or {@code ERROR} and have none of the
     * three elsewhere; every thread but main is created exactly once, by another thread, and every {@code TJOIN}
     * names a created thread that ended with {@code END}; every {@code rf} names a {@code SEND} to the receiving
     * thread, at most once, and {@code rf = null} only for a {@code try} receive; {@code NONDET} has {@code n >= 2},
     * {@code 0 <= i < n} and no {@code lo} (a JMC file); an opaque value never in a send a visible thread performs or
     * reads; names without control characters or line separators; the export's model, visible and header roles;
     * {@code order} and {@code iteration}; the order relation (program order, reads-from, creation, join) acyclic.
     * Whether {@code line} is the graph's summary is the renderer's {@code --check}.
     */
    static String graphProblem(JsonObject g) {
        try {
            return graphProblemOrThrow(g);
        } catch (RuntimeException e) {
            return "malformed: " + e;
        }
    }

    private static boolean keysAre(JsonObject o, String... keys) {
        return new ArrayList<>(o.keySet()).equals(List.of(keys));
    }

    private static boolean printableName(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || (c >= 0x7f && c <= 0x9f) || c == '\u2028' || c == '\u2029') {
                return false;
            }
        }
        return true;
    }

    private static boolean nullOr(JsonElement e, java.util.function.Predicate<JsonElement> p) {
        return e.isJsonNull() || p.test(e);
    }

    private static boolean isString(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    /** A JSON integer that fits an {@code int}, read exactly (no rounding, no wrap-around). */
    private static boolean isInt(JsonElement e) {
        return integer(e, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** A JSON integer within {@code [lo, hi]}, read exactly. */
    private static boolean integer(JsonElement e, long lo, long hi) {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        String t = e.getAsString();
        if (!t.matches("-?(0|[1-9][0-9]*)")) {
            return false;
        }
        java.math.BigInteger b = new java.math.BigInteger(t);
        return b.compareTo(java.math.BigInteger.valueOf(lo)) >= 0 && b.compareTo(java.math.BigInteger.valueOf(hi)) <= 0;
    }

    /**
     * A value in the summary encoding: an integer within signed 64 bits, a boolean, a string, a list of values, or a
     * record {@code {"t":<string>,"f":[values]}}; nothing else (no float, no null, no other object).
     */
    private static boolean encodedValue(JsonElement v) {
        if (v.isJsonPrimitive()) {
            if (v.getAsJsonPrimitive().isNumber()) {
                return integer(v, Long.MIN_VALUE, Long.MAX_VALUE);
            }
            return true;
        }
        if (v.isJsonArray()) {
            for (JsonElement x : v.getAsJsonArray()) {
                if (!encodedValue(x)) {
                    return false;
                }
            }
            return true;
        }
        if (v.isJsonObject()) {
            JsonObject o = v.getAsJsonObject();
            return keysAre(o, "t", "f") && isString(o.get("t")) && o.get("f").isJsonArray() && encodedValue(o.get("f"));
        }
        return false;
    }

    /** A role list of the header: distinct strings sorted by their bytes, each a valid role name. */
    private static String roleListProblem(JsonArray roles, String which) {
        String prev = null;
        for (JsonElement r : roles) {
            if (!isString(r)) {
                return "the export's " + which + " list holds a non-string";
            }
            String name = r.getAsString();
            boolean ascii = !name.isEmpty();
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                ascii &= c >= 0x20 && c < 0x7f;
            }
            if (!ascii || name.equals("main") || name.startsWith("~")) {
                return "the export's " + which + " list holds the name " + r + " (non-empty ASCII, not main, not ~...)";
            }
            if (prev != null && org.mpi_sws.jmc.mp.summary.MpValueEncoder.compareBytes(prev, name) >= 0) {
                return "the export's " + which + " list is not sorted and distinct";
            }
            prev = name;
        }
        return null;
    }

    private static String graphProblemOrThrow(JsonObject g) {
        if (!keysAre(g, "witness", "line", "export", "run", "join", "nearest", "threads")) {
            return "the top-level keys are " + g.keySet();
        }
        if (!integer(g.get("witness"), 1, 1)) {
            return "witness format " + g.get("witness") + " is not 1";
        }
        if (!isString(g.get("line")) || !g.get("export").isJsonObject() || !g.get("run").isJsonObject()
                || !nullOr(g.get("join"), JsonElement::isJsonObject) || !nullOr(g.get("nearest"), JsonElement::isJsonArray)
                || !g.get("threads").isJsonArray()) {
            return "a top-level value has the wrong type";
        }
        JsonObject export = g.getAsJsonObject("export");
        for (String k : List.of("visible", "roles")) {
            if (!export.has(k) || !export.get(k).isJsonArray()) {
                return "the export header has no " + k + " list";
            }
        }
        if (!export.has("model") || !Set.of("p2p", "asyn", "cd").contains(export.get("model").getAsString())) {
            return "the export model is " + export.get("model");
        }
        for (String k : List.of("visible", "roles")) {
            String problem = roleListProblem(export.getAsJsonArray(k), k);
            if (problem != null) {
                return problem;
            }
        }
        List<String> visible = new ArrayList<>();
        export.getAsJsonArray("visible").forEach(v -> visible.add(v.getAsString()));
        List<String> headerRoles = new ArrayList<>();
        export.getAsJsonArray("roles").forEach(v -> headerRoles.add(v.getAsString()));
        if (!headerRoles.containsAll(visible)) {
            return "the export's roles " + headerRoles + " do not hold its visible roles " + visible;
        }
        JsonObject run = g.getAsJsonObject("run");
        if (!keysAre(run, "tool", "tool_commit", "entry", "side", "args", "model", "options", "source", "iteration",
                "producer", "order")) {
            return "the run keys are " + run.keySet();
        }
        if (!isString(run.get("tool")) || !isString(run.get("tool_commit")) || !isString(run.get("entry"))
                || !nullOr(run.get("side"), MpLauncher::isString)
                || !run.get("args").isJsonArray() || !nullOr(run.get("model"), MpLauncher::isString)
                || !run.get("options").isJsonObject()
                || !nullOr(run.get("iteration"), x -> integer(x, 1, Integer.MAX_VALUE))
                || !isString(run.get("producer")) || !isString(run.get("order"))) {
            return "a run value has the wrong type";
        }
        for (JsonElement a : run.getAsJsonArray("args")) {
            if (!isString(a)) {
                return "an args element is not a string";
            }
        }
        JsonElement source = run.get("source");
        if (isString(source)) {
            if (!source.getAsString().equals("unknown")) {
                return "source is the string " + source + ", not \"unknown\"";
            }
        } else if (source.isJsonObject()) {
            JsonObject so = source.getAsJsonObject();
            if (!keysAre(so, "root", "files") || !isString(so.get("root")) || !so.get("files").isJsonObject()) {
                return "source is not {\"root\",\"files\"}";
            }
            for (Map.Entry<String, JsonElement> f : so.getAsJsonObject("files").entrySet()) {
                if (!isString(f.getValue())) {
                    return "the source file " + f.getKey() + " has no md5 string";
                }
            }
        } else {
            return "source is neither an object nor \"unknown\"";
        }
        if (!Set.of("export", "online", "conf").contains(run.get("producer").getAsString())) {
            return "producer " + run.get("producer") + " is not export, online or conf";
        }
        String order = run.get("order").getAsString();
        if (!Set.of("first", "smallest").contains(order)) {
            return "order " + order + " is not first or smallest";
        }
        if (run.get("iteration").isJsonNull() && order.equals("first")) {
            return "iteration is null with order first";
        }
        JsonArray threads = g.getAsJsonArray("threads");
        int n = threads.size();
        if (n == 0) {
            return "no thread";
        }
        // Per event: its kind, for the reference checks below.
        List<List<String>> kinds = new ArrayList<>();
        Map<String, Integer> nameCount = new HashMap<>();
        for (int t = 0; t < n; t++) {
            if (!threads.get(t).isJsonObject()) {
                return "thread " + t + " is not an object";
            }
            JsonObject th = threads.get(t).getAsJsonObject();
            if (!keysAre(th, "name", "daemon", "events") || !nullOr(th.get("name"), MpLauncher::isString)
                    || !th.get("daemon").isJsonPrimitive() || !th.get("daemon").getAsJsonPrimitive().isBoolean()
                    || !th.get("events").isJsonArray()) {
                return "thread " + t + " is not {name, daemon, events}";
            }
            String name = th.get("name").isJsonNull() ? null : th.get("name").getAsString();
            if (name != null) {
                if (!printableName(name)) {
                    return "thread " + t + "'s name holds a control character or a line separator";
                }
                nameCount.merge(name, 1, Integer::sum);
            }
            if (t == 0 && (!"main".equals(name) || th.get("daemon").getAsBoolean())) {
                return "thread 0 is not the non-daemon main";
            }
            JsonArray ev = th.getAsJsonArray("events");
            if (ev.isEmpty()) {
                return "thread " + t + " has no event";
            }
            List<String> ks = new ArrayList<>();
            for (int k = 0; k < ev.size(); k++) {
                if (!ev.get(k).isJsonObject() || !ev.get(k).getAsJsonObject().has("e")
                        || !isString(ev.get(k).getAsJsonObject().get("e"))) {
                    return "(" + t + ", " + k + ") is not an event";
                }
                JsonObject e = ev.get(k).getAsJsonObject();
                String kind = e.get("e").getAsString();
                boolean shape = switch (kind) {
                    case "BEGIN", "END", "BLK" -> keysAre(e, "e");
                    case "TCREATE", "TJOIN" -> keysAre(e, "e", "child") && isInt(e.get("child"));
                    case "SEND" -> keysAre(e, "e", "to", "v") && isInt(e.get("to"));
                    case "RECV" -> keysAre(e, "e", "mode", "rf") && isString(e.get("mode"))
                            && Set.of("block", "try").contains(e.get("mode").getAsString())
                            && nullOr(e.get("rf"), x -> x.isJsonArray() && x.getAsJsonArray().size() == 2
                            && isInt(x.getAsJsonArray().get(0)) && isInt(x.getAsJsonArray().get(1)));
                    case "NONDET" -> keysAre(e, "e", "n", "i") && isInt(e.get("n")) && isInt(e.get("i"));
                    case "ERROR" -> keysAre(e, "e", "msg") && isString(e.get("msg"));
                    default -> false;
                };
                if (!shape) {
                    return "(" + t + ", " + k + ") is not a well-formed event of format 1 (a JMC file has no lo)";
                }
                if (kind.equals("ERROR")) {
                    return "(" + t + ", " + k + ") is an ERROR event, which a JMC graph never has";
                }
                boolean last = k == ev.size() - 1;
                boolean terminal = kind.equals("END") || kind.equals("BLK");
                if ((k == 0) != kind.equals("BEGIN") || last != terminal) {
                    return "(" + t + ", " + k + ") " + kind + " is out of place";
                }
                if (kind.equals("NONDET")) {
                    int on = e.get("n").getAsInt();
                    int oi = e.get("i").getAsInt();
                    if (on < 2 || oi < 0 || oi >= on) {
                        return "(" + t + ", " + k + ") chooses " + oi + " of " + on;
                    }
                }
                ks.add(kind);
            }
            kinds.add(ks);
        }
        for (String v : visible) {
            if (nameCount.getOrDefault(v, 0) != 1) {
                return "the visible role " + v + " is the name of " + nameCount.getOrDefault(v, 0) + " threads";
            }
        }
        int[] creator = new int[n];
        java.util.Arrays.fill(creator, -1);
        Set<String> read = new HashSet<>();
        Set<String> readByVisible = new HashSet<>();
        for (int t = 0; t < n; t++) {
            JsonArray ev = threads.get(t).getAsJsonObject().getAsJsonArray("events");
            String name = threads.get(t).getAsJsonObject().get("name").isJsonNull() ? null
                    : threads.get(t).getAsJsonObject().get("name").getAsString();
            for (int k = 0; k < ev.size(); k++) {
                JsonObject e = ev.get(k).getAsJsonObject();
                switch (kinds.get(t).get(k)) {
                    case "TCREATE" -> {
                        int c = e.get("child").getAsInt();
                        if (c <= 0 || c >= n || c == t || creator[c] >= 0) {
                            return "(" + t + ", " + k + ") creates thread " + c + " again, itself or out of range";
                        }
                        creator[c] = t;
                    }
                    case "SEND" -> {
                        int to = e.get("to").getAsInt();
                        if (to < 0 || to >= n) {
                            return "(" + t + ", " + k + ") sends to no thread";
                        }
                    }
                    case "RECV" -> {
                        JsonElement rf = e.get("rf");
                        if (rf.isJsonNull()) {
                            if (!e.get("mode").getAsString().equals("try")) {
                                return "(" + t + ", " + k + ") reads nothing in a blocking receive";
                            }
                            continue;
                        }
                        int st = rf.getAsJsonArray().get(0).getAsInt();
                        int sk = rf.getAsJsonArray().get(1).getAsInt();
                        if (st < 0 || st >= n || sk < 0 || sk >= kinds.get(st).size()
                                || !kinds.get(st).get(sk).equals("SEND")
                                || threads.get(st).getAsJsonObject().getAsJsonArray("events").get(sk).getAsJsonObject()
                                .get("to").getAsInt() != t) {
                            return "(" + t + ", " + k + ") reads (" + st + ", " + sk + "), not a send to it";
                        }
                        if (!read.add(st + "," + sk)) {
                            return "(" + st + ", " + sk + ") is read twice";
                        }
                        if (name != null && visible.contains(name)) {
                            readByVisible.add(st + "," + sk);
                        }
                    }
                    default -> {
                        // checked above or below
                    }
                }
            }
        }
        for (int c = 1; c < n; c++) {
            if (creator[c] < 0) {
                return "thread " + c + " is never created";
            }
        }
        for (int t = 0; t < n; t++) {
            JsonObject th = threads.get(t).getAsJsonObject();
            JsonArray ev = th.getAsJsonArray("events");
            boolean vis = !th.get("name").isJsonNull() && visible.contains(th.get("name").getAsString());
            for (int k = 0; k < ev.size(); k++) {
                JsonObject e = ev.get(k).getAsJsonObject();
                String kind = kinds.get(t).get(k);
                if (kind.equals("TJOIN")) {
                    int c = e.get("child").getAsInt();
                    if (c <= 0 || c >= n || c == t || !kinds.get(c).get(kinds.get(c).size() - 1).equals("END")) {
                        return "(" + t + ", " + k + ") joins thread " + c + ", which is not a created thread that ended";
                    }
                }
                if (kind.equals("SEND")) {
                    JsonElement v = e.get("v");
                    boolean opaque = v.isJsonObject() && v.getAsJsonObject().has("opaque");
                    if (opaque && (!keysAre(v.getAsJsonObject(), "opaque") || !isString(v.getAsJsonObject().get("opaque")))) {
                        return "(" + t + ", " + k + ") has a malformed opaque value";
                    }
                    if (opaque && (vis || readByVisible.contains(t + "," + k))) {
                        return "(" + t + ", " + k + ") is an opaque value a visible thread sends or reads";
                    }
                    if (!opaque && !encodedValue(v)) {
                        return "(" + t + ", " + k + ") holds a value outside the summary encoding";
                    }
                }
            }
        }
        // Acyclicity of program order, reads-from, creation and join (Kahn's algorithm over all events).
        Map<String, List<String>> succ = new HashMap<>();
        Map<String, Integer> indeg = new HashMap<>();
        java.util.function.BiConsumer<String, String> edge = (a, b) -> {
            succ.computeIfAbsent(a, x -> new ArrayList<>()).add(b);
            indeg.merge(b, 1, Integer::sum);
        };
        int total = 0;
        for (int t = 0; t < n; t++) {
            JsonArray ev = threads.get(t).getAsJsonObject().getAsJsonArray("events");
            for (int k = 0; k < ev.size(); k++) {
                total++;
                String me = t + "," + k;
                indeg.putIfAbsent(me, 0);
                if (k > 0) {
                    edge.accept(t + "," + (k - 1), me);
                }
                JsonObject e = ev.get(k).getAsJsonObject();
                switch (kinds.get(t).get(k)) {
                    case "RECV" -> {
                        if (!e.get("rf").isJsonNull()) {
                            JsonArray rf = e.getAsJsonArray("rf");
                            edge.accept(rf.get(0).getAsInt() + "," + rf.get(1).getAsInt(), me);
                        }
                    }
                    case "TCREATE" -> edge.accept(me, e.get("child").getAsInt() + ",0");
                    case "TJOIN" -> {
                        int c = e.get("child").getAsInt();
                        edge.accept(c + "," + (kinds.get(c).size() - 1), me);
                    }
                    default -> {
                        // no other edge
                    }
                }
            }
        }
        java.util.ArrayDeque<String> ready = new java.util.ArrayDeque<>();
        indeg.forEach((k, d) -> {
            if (d == 0) {
                ready.add(k);
            }
        });
        int seen = 0;
        while (!ready.isEmpty()) {
            String x = ready.poll();
            seen++;
            for (String y : succ.getOrDefault(x, List.of())) {
                if (indeg.merge(y, -1, Integer::sum) == 0) {
                    ready.add(y);
                }
            }
        }
        if (seen != total) {
            return "the order relation (program order, reads-from, creation, join) has a cycle";
        }
        return null;
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
     * sequential-order one, its time the earliest miss in wall time. After an abort the
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
