package org.mpi_sws.jmc.mp.j8;

import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.checker.JmcModelCheckerReport;
import org.mpi_sws.jmc.mp.graph.GraphCanon;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Summary;
import org.mpi_sws.jmc.mp.summary.SummaryExporter;
import org.mpi_sws.jmc.strategies.must.MustStrategy;
import org.mpi_sws.jmc.strategies.must.split.ItemDriver;
import org.mpi_sws.jmc.strategies.must.split.ItemResult;
import org.mpi_sws.jmc.strategies.must.split.TypedGraphEncoding;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * J8 round 02 (tester): one run per JVM, printed as lines for {@link J8Step0Test}.
 *
 * <pre>
 * seq   &lt;class[:args]&gt; &lt;count|export&gt; &lt;outFile&gt;
 * split &lt;class[:args]&gt; &lt;count|export&gt; &lt;K&gt; &lt;SEQUENTIAL|REVERSED&gt; &lt;onlyItem|-&gt; &lt;outFile&gt;
 * </pre>
 *
 * <p>{@code seq} runs the unchanged sequential strategy (no split control) and records, at the end
 * of every counted iteration, the graph's {@code GraphCanon}, whether it is blocked, and its outcome
 * digest ({@link TypedGraphEncoding#outcome} over the strategy's name table, read by reflection); it
 * checks {@code digest equal ⇔ GraphCanon equal} over all graphs of the run, and builds the export as
 * {@code MpLauncher} does. {@code split} runs {@link ItemDriver#run}. Lines: {@code RESULT}, {@code
 * COUNTS}, {@code OUT c|b digest} (seq), {@code SEG index iterations blocked outcomes} and {@code SUM
 * index json} (split), {@code EXPORT line}, {@code IFF violations}.
 */
public final class J8Step0Probe {

    private J8Step0Probe() {}

    /** The sequential strategy with outcome capture (test code; reflection on private state). */
    static final class Capturing extends MustStrategy {
        final List<String> flags = new ArrayList<>();
        final List<String> digests = new ArrayList<>();
        final List<GraphCanon> canons = new ArrayList<>();
        private final Field active;
        private final Field nameOfId;
        private final Method countsAsBlocked;

        Capturing(List<String> roles, List<String> visible) throws ReflectiveOperationException {
            super(1L, "build/test-results/jmc-report", roles, visible);
            active = MustStrategy.class.getDeclaredField("active");
            active.setAccessible(true);
            nameOfId = MustStrategy.class.getDeclaredField("nameOfId");
            nameOfId.setAccessible(true);
            countsAsBlocked = MustStrategy.class.getDeclaredMethod("countsAsBlocked");
            countsAsBlocked.setAccessible(true);
        }

        @Override
        public void resetIteration(int iteration) {
            GraphCanon canon = null;
            boolean blocked = false;
            try {
                if ((boolean) active.get(this)) {
                    canon = currentGraph().canon();
                    blocked = (boolean) countsAsBlocked.invoke(this);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            int before = iterations();
            super.resetIteration(iteration);
            if (iterations() > before && canon != null) {
                @SuppressWarnings("unchecked")
                Map<Long, String> names;
                try {
                    names = new HashMap<>((Map<Long, String>) nameOfId.get(this));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
                flags.add(blocked ? "b" : "c");
                String digest;
                try {
                    digest = TypedGraphEncoding.sha256(TypedGraphEncoding.outcome(canon, names::get));
                } catch (IllegalArgumentException e) {
                    // the typed encoding refuses this graph (split mode would); the sequential run goes on
                    digest = "REFUSED";
                }
                digests.add(digest);
                canons.add(canon);
            }
        }
    }

    static Runnable instantiate(String spec) throws ReflectiveOperationException {
        int colon = spec.indexOf(':');
        String className = colon < 0 ? spec : spec.substring(0, colon);
        String[] args = colon < 0 ? null : spec.substring(colon + 1).split(",", -1);
        Class<?> c = Class.forName(className);
        return (Runnable) (args == null ? c.getDeclaredConstructor().newInstance()
                : c.getDeclaredConstructor(String[].class).newInstance((Object) args));
    }

    static String clean(String s) {
        return s == null ? "-" : s.replace('\n', ' ').replace('\r', ' ');
    }

    static List<String> exportLines(MpProgram mp, int ran, Map<Summary, Integer> summaries) {
        List<String> lines = new ArrayList<>();
        lines.add(SummaryExporter.header(mp.programName(), mp.size(), mp.visibleRoles(), mp.model(), "complete",
                1L, ran));
        for (Map.Entry<Summary, Integer> e : summaries.entrySet()) {
            lines.add(e.getKey().toJson(e.getValue()));
        }
        return lines;
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String spec = args[1];
        boolean export = args[2].equals("export");
        Runnable program = instantiate(spec);
        MpProgram mp = export ? (MpProgram) program : null;
        Path out = Path.of(args[args.length - 1]);
        List<String> lines = new ArrayList<>();
        if (mode.equals("seq")) {
            Capturing s = export ? new Capturing(mp.rolesInSpawnOrder(), mp.visibleRoles()) : new Capturing(null, null);
            String failure = null;
            JmcModelCheckerReport report = null;
            try {
                report = new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType("must")
                        .numIterations(0).seed(1L).reportPath("build/test-results/jmc-report")
                        .strategyConstructor(c -> s).timeout(Duration.ofSeconds(600)).build())
                        .check(new JmcFunctionalTestTarget(spec, () -> MpProcess.runMain(program)));
            } catch (Throwable t) {
                failure = t.toString();
            }
            int ran = report == null ? -1 : report.getTotalIterations();
            lines.add("RESULT split=false exhausted=" + s.exhausted() + " failure=" + clean(failure));
            lines.add("COUNTS complete=" + s.distinctCompleteGraphs() + " blocked=" + s.distinctBlockedGraphs()
                    + " ran=" + ran + " blockedIterations=" + (report == null ? -1 : report.getBlockedIterations()));
            for (int i = 0; i < s.digests.size(); i++) {
                lines.add("OUT " + s.flags.get(i) + " " + s.digests.get(i));
            }
            // digest equal <=> GraphCanon equal, over every pair of graphs of the run
            int violations = 0;
            Map<String, Set<GraphCanon>> byDigest = new HashMap<>();
            Map<GraphCanon, Set<String>> byCanon = new HashMap<>();
            for (int i = 0; i < s.digests.size(); i++) {
                byDigest.computeIfAbsent(s.digests.get(i), d -> new HashSet<>()).add(s.canons.get(i));
                byCanon.computeIfAbsent(s.canons.get(i), d -> new HashSet<>()).add(s.digests.get(i));
            }
            for (Set<GraphCanon> v : byDigest.values()) {
                violations += v.size() - 1;
            }
            for (Set<String> v : byCanon.values()) {
                violations += v.size() - 1;
            }
            lines.add("IFF " + violations + " graphs=" + s.digests.size() + " distinctDigests=" + byDigest.size()
                    + " distinctCanons=" + byCanon.size());
            if (export) {
                for (String l : exportLines(mp, ran, s.summaries())) {
                    lines.add("EXPORT " + l);
                }
            }
        } else {
            int k = Integer.parseInt(args[3]);
            ItemDriver.Order order = ItemDriver.Order.valueOf(args[4]);
            OptionalInt only = args[5].equals("-") ? OptionalInt.empty() : OptionalInt.of(Integer.parseInt(args[5]));
            ItemDriver.Result r = ItemDriver.run(new ItemDriver.Config(spec + " " + args[2], program,
                    export ? mp.rolesInSpawnOrder() : null, export ? mp.visibleRoles() : null, k, order, only, 600));
            lines.add("RESULT split=" + r.split() + " r=" + r.r() + " n=" + r.n() + " fp=" + r.fingerprint()
                    + " failure=" + clean(r.failure()));
            lines.add("COUNTS complete=" + r.distinctComplete() + " blocked=" + r.distinctBlocked() + " ran=" + r.ran()
                    + " blockedIterations=" + r.blockedIterations());
            List<ItemResult> segs = new ArrayList<>();
            if (r.root() != null) {
                segs.add(r.root());
            }
            segs.addAll(r.items());
            for (ItemResult s : segs) {
                StringBuilder b = new StringBuilder("SEG " + s.index() + " " + s.iterations() + " "
                        + s.blockedIterations() + " ");
                List<String> os = new ArrayList<>();
                for (ItemResult.Outcome o : s.outcomes()) {
                    os.add((o.blocked() ? "b:" : "c:") + o.digest());
                }
                b.append(os.isEmpty() ? "-" : String.join(",", os));
                lines.add(b.toString());
                if (s.summaries() != null) {
                    for (Map.Entry<Summary, Integer> e : s.summaries().entrySet()) {
                        lines.add("SUM " + s.index() + " " + e.getKey().toJson(e.getValue()));
                    }
                }
            }
            if (export && r.summaries() != null) {
                for (String l : exportLines(mp, r.ran(), r.summaries())) {
                    lines.add("EXPORT " + l);
                }
            }
        }
        lines.add("OBSERVED " + clean(program.toString()));
        Files.write(out, lines, StandardCharsets.UTF_8);
        PrintStream o = System.out;
        o.println("probe done " + out);
        System.exit(0);
    }
}
