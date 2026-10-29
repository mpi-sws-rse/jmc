package org.mpi_sws.jmc.mp.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.checker.JmcFunctionalTestTarget;
import org.mpi_sws.jmc.checker.JmcModelChecker;
import org.mpi_sws.jmc.mp.events.EventKey;
import org.mpi_sws.jmc.mp.events.MpLabel;
import org.mpi_sws.jmc.mp.graph.ExecutionGraph;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.programs.MustSection13;
import org.mpi_sws.jmc.mp.programs.j1.AssertsInMainAt3;
import org.mpi_sws.jmc.mp.programs.j1.BlockedAfterOne;
import org.mpi_sws.jmc.mp.programs.j1.BlockedAlone;
import org.mpi_sws.jmc.mp.programs.j1.NondetSend;
import org.mpi_sws.jmc.mp.programs.j1.ProcessRelay;
import org.mpi_sws.jmc.mp.programs.j1.SRnb;
import org.mpi_sws.jmc.mp.programs.j1.SendsTid;
import org.mpi_sws.jmc.mp.programs.j1.SpawnBeforeJoin;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.util.List;
import java.util.Set;

/**
 * Tester tests for J1 rounds 01–04, C2(d) extended. Since round 04 the recorder refuses a program
 * whose main performs a send or receive, spawns after its first completed await, or whose processes
 * spawn or await processes (SUMMARY-FORMAT.md, ruling of 2026-10-26); the fixtures of rounds 02–03
 * that break this ({@code MainSendsAfterJoin}, {@code MainSendsBeforeJoin}, {@code MainRecvThenSpawn},
 * {@code SpawnFirstThenMainRecv}, {@code SpawnAfterJoin}, {@code JoinChainSpawn},
 * {@code JoinChainSpawnEarly}) are refusal tests in {@code MpLauncherExportTest}. What stays here:
 * counts through an invisible process, the in-shape {@code SpawnBeforeJoin}, and a check that no
 * accepted fixture's graph carries an hb (spawn/join) edge.
 */
class J1GoldenExtraTest {

    static final long SEED = 20261026L;
    static final String V1 = "{\"t\":\"V\",\"f\":[1]}";

    static final String PROCESS_RELAY = "{\"statuses\":{\"a\":\"done\",\"b\":\"done\"},\"words\":{\"a\":[{\"s\":" + V1
            + "}],\"b\":[{\"r\":" + V1 + "}]},\"counts\":[0,1]}";

    @Test
    void c2d_golden_ProcessRelay_invisibleProcess() throws Exception {
        J1GoldenTest.Run r = J1GoldenTest.run(new ProcessRelay(), 200, SEED);
        assertNull(r.report().getErrorMessage());
        J1GoldenTest.assertLines(J1GoldenTest.set(PROCESS_RELAY), r, 200);
    }

    @Test
    void c2d_detects_ProcessRelay_countsNotThroughM() {
        J1GoldenTest.mutant("C2d process relay", () -> {
            J1GoldenTest.Run r = J1GoldenTest.run(new ProcessRelay(), 200, SEED);
            J1GoldenTest.assertLines(J1GoldenTest.set(PROCESS_RELAY.replace("[0,1]", "[0,0]")), r, 200);
        });
    }

    static final String SPAWN_BEFORE_JOIN = "{\"statuses\":{\"a\":\"done\",\"b\":\"done\"},\"words\":{\"a\":[{\"r\":null}],"
            + "\"b\":[{\"r\":null}]},\"counts\":[0,0]}";

    @Test
    void m2_golden_spawnBeforeJoin() throws Exception {
        J1GoldenTest.Run r = J1GoldenTest.run(new SpawnBeforeJoin(), 100, SEED);
        assertNull(r.report().getErrorMessage());
        J1GoldenTest.assertLines(J1GoldenTest.set(SPAWN_BEFORE_JOIN), r, 100);
    }

    @Test
    void m2_detects_spawnBeforeJoin_mutatedLine() {
        J1GoldenTest.mutant("M2 spawn before join, mutated line", () -> {
            J1GoldenTest.Run r = J1GoldenTest.run(new SpawnBeforeJoin(), 100, SEED);
            J1GoldenTest.assertLines(J1GoldenTest.set(SPAWN_BEFORE_JOIN.replace("[0,0]", "[0,1]")), r, 100);
        });
    }

    /**
     * No spawn/join edge into a message event, in the exportable fixtures (main with no message
     * events, spawns first, awaits last): owner's ruling Q-J2b-5, 2026-10-27, which corrects the
     * earlier "into or out of" (every join edge leaves the joined process's last event).
     */
    @Test
    void edges_noneInAcceptedFixtures() throws Exception {
        // J2b round 02 (owner's rulings Q2 and Q-J2b-5): narrowed from "no hb edge" to "no spawn/join
        // edge into a message event". Since J2b (F4) main ends with a Block when it awaits a blocked process, and
        // that Block carries the porf predecessor of an earlier completed await (BlockedAfterOne); a
        // summary has message events only, so such an edge reaches none of them.
        List<MpProgram> accepted = List.of(new MustSection13(), new SRnb(), new BlockedAfterOne(), new BlockedAlone(),
                new NondetSend(), new SendsTid(), new ProcessRelay(), new SpawnBeforeJoin(), new AssertsInMainAt3());
        for (MpProgram p : accepted) {
            J1GoldenTest.Run r = J1GoldenTest.run(p, 2, SEED);
            ExecutionGraph g = r.rec().lastGraph();
            System.out.println("EDGES " + p.getClass().getSimpleName() + ": " + g.hb());
            for (ExecutionGraph.Edge e : g.hb()) {
                MpLabel to = g.event(e.to()).label();
                assertTrue(!(to instanceof MpLabel.Send) && !(to instanceof MpLabel.Recv),
                        "hb edge into a message event in " + p.getClass().getSimpleName() + ": " + e);
            }
        }
    }

    @Test
    void edges_blockedAwait_poOnly() throws Exception {
        // J2b round 02, Q2 (TraceForge Block(Join): program order only). BlockedAfterOne: r (task 2)
        // receives twice, s (task 3) sends once; main awaits s, then r, which ends blocked. Derived:
        // main's only event is its Block (1,0); it carries the edge from s's send (3,0), the porf
        // predecessor of the completed await of s, and no edge from r.
        J1GoldenTest.Run r = J1GoldenTest.run(new BlockedAfterOne(), 2, SEED);
        ExecutionGraph g = r.rec().lastGraph();
        System.out.println("EDGES BlockedAfterOne (recorder): " + g.hb() + " main: " + g.thread(1L));
        assertTrue(g.event(new EventKey(1L, 0)).label() instanceof MpLabel.Block, "main's event is a Block");
        assertEquals(Set.of(new ExecutionGraph.Edge(new EventKey(3L, 0), new EventKey(1L, 0))), g.hb(), "hb edges");
    }

    @Test
    void edges_blockedAwait_poOnly_must() throws Exception {
        // the same under must (stable ids: r = m/0 = 2, s = m/1 = 3); one blocked graph
        BlockedAfterOne p = new BlockedAfterOne();
        MustStrategy s = new MustStrategy(SEED, "build/test-results/j1-tester/must-bao", p.rolesInSpawnOrder(),
                p.visibleRoles());
        new JmcModelChecker(new JmcCheckerConfiguration.Builder().strategyType("must").strategyConstructor(c -> s)
                .numIterations(100).seed(SEED).reportPath("build/test-results/j1-tester/must-bao").build())
                .check(new JmcFunctionalTestTarget(p.getClass().getName(), p::run));
        ExecutionGraph g = s.currentGraph();
        System.out.println("EDGES BlockedAfterOne (must): " + g.hb() + " main: " + g.thread(1L));
        assertEquals(1, s.distinctBlockedGraphs(), "one blocked graph");
        assertTrue(g.event(new EventKey(1L, 0)).label() instanceof MpLabel.Block, "main's event is a Block");
        assertEquals(Set.of(new ExecutionGraph.Edge(new EventKey(3L, 0), new EventKey(1L, 0))), g.hb(), "hb edges");
    }
}
