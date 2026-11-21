package org.mpi_sws.jmc.mp.cli;

import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The launcher's named form: a subcommand ({@code count}, {@code export}, {@code online} or {@code replay}) followed by
 * named options, and {@code --help}. A named invocation is translated into the positional arguments that
 * {@link MpLauncher} runs, so that both forms run the same code; this class only parses, checks which options a
 * subcommand takes, fills in the defaults and prints the help.
 */
final class NamedCli {

    /** The subcommands, in the order the help lists them. */
    static final List<String> COMMANDS = List.of("count", "export", "online", "replay");

    private static final Set<String> HELP = Set.of("--help", "-h", "help");

    /** The options that take no value. */
    private static final Set<String> FLAGS =
            Set.of("--stop-at-first", "--allow-source-change", "--allow-tool-change", "--help", "-h");

    /** The options that take two values. */
    private static final Set<String> TWO_VALUES = Set.of("--worker");

    /** The options of a run (count, export, online), common to the three. */
    private static final List<String> RUN_OPTIONS = List.of("--program", "--strategy", "--model", "--iterations",
            "--seed", "--report-dir", "--timeout", "--split", "--k", "--worker");

    /** The options each subcommand accepts, besides {@code --help}. */
    private static final Map<String, Set<String>> ACCEPTED = Map.of(
            "count", Set.copyOf(RUN_OPTIONS),
            "export", union(RUN_OPTIONS, List.of("--out", "--graphs", "--graphs-limit")),
            "online", union(RUN_OPTIONS,
                    List.of("--spec", "--out", "--stop-at-first", "--witness", "--graphs", "--graphs-limit")),
            "replay", Set.of("--graph", "--report", "--allow-source-change", "--allow-tool-change", "--program"));

    private NamedCli() {}

    /** A parsed named invocation: run {@code args} (positional form) as {@code command}, or exit with a status. */
    sealed interface Parsed permits Run, Exit {}

    /**
     * A named invocation to run.
     *
     * @param command the subcommand
     * @param args the equivalent positional arguments
     * @param model the model given with {@code --model}, or {@code null}
     */
    record Run(String command, String[] args, CommunicationModel model) implements Parsed {}

    /**
     * An invocation that ends without a run: the help (status 0) or a usage error (status 2).
     *
     * @param status the exit status
     */
    record Exit(int status) implements Parsed {}

    /**
     * Whether the arguments are in the named form: the first one is a subcommand or asks for help.
     *
     * @param args the command-line arguments
     * @return {@code true} for the named form
     */
    static boolean isNamed(String[] args) {
        return args.length > 0 && (COMMANDS.contains(args[0]) || HELP.contains(args[0]));
    }

    /**
     * Parses a named invocation.
     *
     * @param args the command-line arguments, {@link #isNamed} holding
     * @param out where the help goes
     * @param err where usage errors go
     * @return what to do
     */
    static Parsed parse(String[] args, PrintStream out, PrintStream err) {
        if (HELP.contains(args[0])) {
            if (args.length == 1) {
                out.print(help(null));
                return new Exit(0);
            }
            if (args.length == 2 && COMMANDS.contains(args[1])) {
                out.print(help(args[1]));
                return new Exit(0);
            }
            return usage(err, null, "help takes at most one subcommand (" + String.join(", ", COMMANDS) + ")");
        }
        String command = args[0];
        Map<String, List<String>> opts = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--help") || a.equals("-h")) {
                out.print(help(command));
                return new Exit(0);
            }
            if (!a.startsWith("-")) {
                return usage(err, command, "unexpected argument " + a + " (every value follows its option)");
            }
            if (!ACCEPTED.get(command).contains(a)) {
                return usage(err, command,
                        isKnown(a) ? a + " is not an option of " + command : "unknown option " + a);
            }
            if (opts.containsKey(a)) {
                return usage(err, command, a + " given twice");
            }
            int arity = FLAGS.contains(a) ? 0 : TWO_VALUES.contains(a) ? 2 : 1;
            if (i + arity >= args.length) {
                return usage(err, command, a + (arity == 2 ? " needs two values" : " needs a value"));
            }
            List<String> values = new ArrayList<>();
            for (int k = 1; k <= arity; k++) {
                String v = args[i + k];
                if (v.startsWith("--") || v.equals("-h")) {
                    return usage(err, command, a + " needs a value before " + v
                            + " (prefix a relative file name with ./ if it begins with -)");
                }
                values.add(v);
            }
            opts.put(a, values);
            i += arity;
        }
        if (command.equals("replay")) {
            return replay(opts, err);
        }
        return run(command, opts, err);
    }

    private static Parsed replay(Map<String, List<String>> opts, PrintStream err) {
        if (!opts.containsKey("--graph")) {
            return usage(err, "replay", "replay needs --graph <graph.json>");
        }
        List<String> a = new ArrayList<>(List.of("--replay", value(opts, "--graph")));
        if (opts.containsKey("--report")) {
            a.add("--report");
            a.add(value(opts, "--report"));
        }
        if (opts.containsKey("--allow-source-change")) {
            a.add("--allow-source-change");
        }
        if (opts.containsKey("--allow-tool-change")) {
            a.add("--allow-tool-change");
        }
        if (opts.containsKey("--program")) {
            a.add(value(opts, "--program"));
        }
        return new Run("replay", a.toArray(new String[0]), null);
    }

    private static Parsed run(String command, Map<String, List<String>> opts, PrintStream err) {
        if (!opts.containsKey("--program")) {
            return usage(err, command, command + " needs --program <class[:args]>");
        }
        String program = value(opts, "--program");
        String strategy = opts.containsKey("--strategy") ? value(opts, "--strategy") : "must";
        if (!strategy.equals("must") && !strategy.equals("mp-random")) {
            return usage(err, command, "--strategy " + strategy
                    + " is not a message-passing strategy (must or mp-random)");
        }
        CommunicationModel model = null;
        if (opts.containsKey("--model")) {
            model = model(value(opts, "--model"));
            if (model == null) {
                return usage(err, command, "unknown model " + value(opts, "--model")
                        + " (p2p or fifo, asyn or bag, cd or causal, mbox)");
            }
            if (strategy.equals("must") && (model == CommunicationModel.CD || model == CommunicationModel.MBOX)) {
                return usage(err, command, "must supports the p2p and asyn models only, not "
                        + canonical(model) + "; mp-random samples every model");
            }
        }
        if (command.equals("export") && !opts.containsKey("--out")) {
            return usage(err, command, "export needs --out <file>");
        }
        if (command.equals("online") && !opts.containsKey("--spec")) {
            return usage(err, command, "online needs --spec <file>");
        }
        String iterations = opts.containsKey("--iterations") ? value(opts, "--iterations")
                : strategy.equals("must") ? "0" : "1000";
        String seed = opts.containsKey("--seed") ? value(opts, "--seed") : Long.toString(System.nanoTime());
        String reportDir = opts.containsKey("--report-dir") ? value(opts, "--report-dir")
                : "build/mp-launch/" + simpleName(program);
        String out = opts.containsKey("--out") ? value(opts, "--out") : "-";
        if (out.equals("-") && opts.containsKey("--out")) {
            return usage(err, command, "--out needs a file, not -");
        }
        String timeout = opts.containsKey("--timeout") ? value(opts, "--timeout") : "600";
        List<String> a = new ArrayList<>(List.of(program, strategy, iterations, seed, reportDir, out, timeout));
        for (String o : List.of("--spec", "--witness", "--graphs", "--graphs-limit", "--split", "--k", "--worker")) {
            if (opts.containsKey(o)) {
                a.add(o);
                a.addAll(opts.get(o));
            }
        }
        if (opts.containsKey("--stop-at-first")) {
            a.add("--stop-at-first");
        }
        return new Run(command, a.toArray(new String[0]), model);
    }

    /**
     * The model a {@code --model} value names, aliases included, or {@code null}.
     *
     * @param name {@code p2p}, {@code fifo}, {@code asyn}, {@code bag}, {@code cd}, {@code causal} or {@code mbox},
     *     in any case
     * @return the model, or {@code null} for an unknown name
     */
    static CommunicationModel model(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "p2p", "fifo" -> CommunicationModel.P2P;
            case "asyn", "bag" -> CommunicationModel.ASYN;
            case "cd", "causal" -> CommunicationModel.CD;
            case "mbox" -> CommunicationModel.MBOX;
            default -> null;
        };
    }

    /**
     * The canonical name of a model, as the export header and the {@code model:} line print it.
     *
     * @param model the model
     * @return {@code p2p}, {@code asyn}, {@code cd} or {@code mbox}
     */
    static String canonical(CommunicationModel model) {
        return model.name().toLowerCase(Locale.ROOT);
    }

    private static String simpleName(String program) {
        int colon = program.indexOf(':');
        String cls = colon < 0 ? program : program.substring(0, colon);
        return cls.substring(Math.max(cls.lastIndexOf('.'), cls.lastIndexOf('$')) + 1);
    }

    private static String value(Map<String, List<String>> opts, String option) {
        return opts.get(option).get(0);
    }

    private static boolean isKnown(String option) {
        for (Set<String> s : ACCEPTED.values()) {
            if (s.contains(option)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> union(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return Set.copyOf(all);
    }

    private static Exit usage(PrintStream err, String command, String why) {
        err.println("MpLauncher: " + why);
        err.println(usageLine(command));
        return new Exit(2);
    }

    /**
     * The one-line usage of the named form, or of one subcommand.
     *
     * @param command the subcommand, or {@code null} for all
     * @return the line, starting with {@code usage:}
     */
    static String usageLine(String command) {
        String head = "usage: MpLauncher ";
        if (command == null) {
            return head + "count|export|online|replay [options]"
                    + "   (MpLauncher --help, MpLauncher <command> --help)";
        }
        return head + synopsis(command) + "   (MpLauncher " + command + " --help)";
    }

    private static String synopsis(String command) {
        String run = "--program <class[:args]> [--strategy must|mp-random] [--model <m>] [--iterations N] [--seed N]"
                + " [--report-dir <dir>] [--timeout S]";
        return switch (command) {
            case "count" -> "count " + run;
            case "export" -> "export " + run + " --out <file> [--graphs <store> [--graphs-limit N]]";
            case "online" -> "online " + run + " --spec <file> [--stop-at-first] [--witness <file or dir/>]"
                    + " [--out <file> [--graphs <store> [--graphs-limit N]]]";
            case "replay" -> "replay --graph <graph.json> [--report <file>] [--allow-source-change]"
                    + " [--allow-tool-change] [--program <class[:args]>]";
            default -> throw new IllegalArgumentException(command);
        };
    }

    private static final String INTRO = """
            Model checks a message-passing program (org.mpi_sws.jmc.api.mp): counts its executions, exports
            their summaries, checks them online against a specification, or replays one execution.

            """;

    private static final String COMMAND_LIST = """

            Commands:
              count    explore the program and print the number of distinct executions
              export   explore and write one summary per distinct execution (--out)
              online   explore and look every execution up in a specification file (--spec)
              replay   re-run one execution from a stored graph (--graph)
            """;

    private static final String RUN_HELP = """

            Options of count, export and online:
              --program <class[:args]>  the program: a Runnable with a public no-argument constructor, or
                                        Class:a,b,... for its public (String...) constructor; export and
                                        online need an MpProgram
              --strategy <s>            must (default; explores every distinct execution; p2p and asyn
                                        only) or mp-random (samples executions; any model)
              --model <m>               the communication model the program uses: p2p (alias fifo), asyn
                                        (bag), cd (causal) or mbox; refused if it differs from the model an
                                        MpProgram declares; printed canonically
              --iterations N            0 means unbounded, until the exploration ends or the timeout (must
                                        only, and its default); default 1000 for mp-random
              --seed N                  default from the clock, printed
              --report-dir <dir>        default build/mp-launch/<program simple name>; its plain files are
                                        deleted at the start of the run
              --timeout S               seconds, default 600 (must)
            """;

    private static final String OUT_HELP = """

            Options of export and online:
              --out <file>              the summary file (export: required; online: optional)
              --graphs <store>          also write the graph of each distinct summary (must, with --out)
              --graphs-limit N          at most N graphs in the store
            """;

    private static final String ONLINE_HELP = """

            Options of online:
              --spec <file>             the specification file (complete; same program, roles and model)
              --stop-at-first           stop at the first execution the specification does not cover
              --witness <file or dir/>  write the first uncovered execution (a file), or one witness per
                                        distinct uncovered summary (a directory ending in /)
            """;

    private static final String REPLAY_HELP = """

            Options of replay:
              --graph <graph.json>      the stored graph (one line of a graph store)
              --report <file>           also write the result as JSON
              --allow-source-change     accept a graph recorded from another source of the program
              --allow-tool-change       accept a graph recorded by another JMC build
              --program <class[:args]>  optional; must match the class and arguments the graph names
            """;

    private static final String ADVANCED_HELP = """

            Advanced (must, iterations 0):
              --split W                 explore with W worker JVMs once the search stack is large enough
              --k K                     the stack size at which to split, default 8*W
              --worker <dir> <id>       internal: how the coordinator starts its workers
            """;

    private static final String POSITIONAL_HELP = """

            The positional form, MpLauncher <program class[:args]> [strategy] [iterations] [seed]
            [reportPath] [exportFile] [timeoutSeconds] [options], is still accepted and is deprecated.
            """;

    /**
     * The help text, for all subcommands or for one.
     *
     * @param command the subcommand, or {@code null}
     * @return the text, ending with a newline
     */
    static String help(String command) {
        StringBuilder b = new StringBuilder(INTRO);
        for (String c : command == null ? COMMANDS : List.of(command)) {
            b.append("  MpLauncher ").append(synopsis(c)).append('\n');
        }
        boolean all = command == null;
        boolean run = all || !command.equals("replay");
        if (all) {
            b.append(COMMAND_LIST);
        }
        if (run) {
            b.append(RUN_HELP);
        }
        if (all || command.equals("export") || command.equals("online")) {
            b.append(OUT_HELP);
        }
        if (all || command.equals("online")) {
            b.append(ONLINE_HELP);
        }
        if (all || command.equals("replay")) {
            b.append(REPLAY_HELP);
        }
        if (run) {
            b.append(ADVANCED_HELP);
        }
        return b.append(POSITIONAL_HELP).toString();
    }
}
