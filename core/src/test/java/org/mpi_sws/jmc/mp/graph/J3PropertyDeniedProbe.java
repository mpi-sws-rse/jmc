package org.mpi_sws.jmc.mp.graph;

import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.lang.reflect.Field;
import java.security.Permission;
import java.util.PropertyPermission;
import java.util.Random;

/**
 * Codex round 03 (M4): a SecurityManager that refuses only the read of {@code jmc.must.checkPrefixes},
 * installed before {@code ExecutionGraph} and the Must {@code Graph} initialise (this JVM loads
 * neither before {@code main} installs it). Argument {@code deny} installs it, {@code none} does not.
 * Prints the two CHECK values (or the class-initialisation failure), the matrix and random-graph
 * verdicts against {@link J3PorfTest}'s reference, and Must runs' graph counts, so that the caller
 * compares a denied run with an unrestricted one, and this tree with 3c43b2d.
 */
@SuppressWarnings("removal")
public final class J3PropertyDeniedProbe {

    private J3PropertyDeniedProbe() {}

    static volatile int refused;

    public static void main(String[] args) throws Exception {
        if (args[0].equals("deny")) {
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPermission(Permission p) {
                    if (p instanceof PropertyPermission && p.getName().equals("jmc.must.checkPrefixes")
                            && p.getActions().contains("read")) {
                        refused++;
                        throw new SecurityException("refused by the test: read jmc.must.checkPrefixes");
                    }
                }

                @Override
                public void checkPermission(Permission p, Object context) {
                    checkPermission(p);
                }
            });
        }
        System.out.println("SM " + (System.getSecurityManager() != null) + " property "
                + (args[0].equals("deny") ? "(not readable)" : System.getProperty("jmc.must.checkPrefixes")));
        System.out.println("EG.CHECK " + init("org.mpi_sws.jmc.mp.graph.ExecutionGraph"));
        System.out.println("GRAPH.CHECK " + init("org.mpi_sws.jmc.strategies.must.Graph"));
        int failed = 0;
        int graphs = 0;
        try {
            for (var e : J3PorfTest.matrix().entrySet()) {
                System.out.println("MATRIX " + e.getKey() + " " + J3PorfTest.check(e.getKey(), e.getValue().get()));
                graphs++;
            }
            Random r = new Random(311);
            for (int i = 0; i < 1000; i++) {
                J3PorfTest.check("random " + i, J3PorfTest.randomGraph(r));
                graphs++;
            }
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL graphs: " + t);
        }
        System.out.println("GRAPHS checked " + graphs);
        for (String p : new String[] {"org.mpi_sws.jmc.mp.programs.MustSection13",
            "org.mpi_sws.jmc.mp.programs.j1.SendsTid", "org.mpi_sws.jmc.strategies.must.C1SpawnJoinBackward",
            "org.mpi_sws.jmc.mp.programs.j1.NondetSend", "org.mpi_sws.jmc.mp.programs.j1.SRnb"}) {
            try {
                Runnable prog = (Runnable) Class.forName(p).getConstructor().newInstance();
                MustStrategy s = new MustStrategy(1L, "build/test-results/speed2-m4");
                JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder().strategyType("must")
                        .strategyConstructor(c -> s).numIterations(10000).seed(1L)
                        .reportPath("build/test-results/speed2-m4").build();
                String verdict;
                try {
                    verdict = String.valueOf(new JmcModelChecker(config).check(new JmcFunctionalTestTarget(p, prog::run))
                            .getErrorMessage());
                } catch (Throwable t) {
                    verdict = "threw " + t;
                }
                System.out.println("MUST " + p + " complete=" + s.distinctCompleteGraphs() + " blocked="
                        + s.distinctBlockedGraphs() + " iterations=" + s.iterations() + " exhausted=" + s.exhausted()
                        + " verdict=" + verdict);
            } catch (Throwable t) {
                failed++;
                System.out.println("MUST " + p + " failed: " + t + (t.getCause() != null ? " <- " + t.getCause() : ""));
            }
        }
        System.out.println("REFUSED reads " + (refused > 0));
        System.out.println("DONE failed=" + failed);
        System.exit(0);
    }

    static String init(String cls) {
        try {
            Class<?> c = Class.forName(cls);
            Field f = c.getDeclaredField("CHECK");
            f.setAccessible(true);
            return String.valueOf(f.getBoolean(null));
        } catch (Throwable t) {
            return "init failed: " + t + (t.getCause() != null ? " <- " + t.getCause() : "");
        }
    }
}
