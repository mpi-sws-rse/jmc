package org.mpi_sws.jmc.strategies.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMC-SPEED tester rounds 01-02 (plan/jmc-must/log/dev/SPEED/round-0{1,2}.prompt.md): the C1 differential
 * matrix. {@link Graph#CHECK} is read once when {@link Graph} loads, so every case runs
 * {@link C1Probe} in a fresh JVM with {@code -Djmc.must.checkPrefixes=true} (guard on) or without it
 * (guard off), and the probe confirms which one it got. Expected values are derived in the probe's
 * comments.
 */
class C1DifferentialTest {

    private static final String GUARD = "-Djmc.must.checkPrefixes=true";

    record Out(int exit, String text) {}

    static Out probe(List<String> jvm, String... args) throws Exception {
        Path dir = Path.of("build/test-results/speed-c1");
        Files.createDirectories(dir);
        Path log = Files.createTempFile(dir, "probe-", ".txt");
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-Xss4m");
        cmd.addAll(jvm);
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(C1Probe.class.getName());
        Collections.addAll(cmd, args);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean done = p.waitFor(900, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            p.waitFor();
        }
        String text = Files.readString(log, StandardCharsets.UTF_8);
        StringBuilder b = new StringBuilder();
        for (String l : text.split("\n")) {
            if (l.startsWith("CHECK ") || l.startsWith("PASS ") || l.startsWith("FAIL ") || l.startsWith("RUN ")
                    || l.startsWith("STATE ") || l.startsWith("NOTE ") || l.startsWith("PROBLEM ")
                    || l.startsWith("DONE ")) {
                b.append(l).append('\n');
            }
        }
        System.out.println("PROBE " + String.join(" ", jvm) + " " + String.join(" ", args) + "\n" + b);
        return new Out(done ? p.exitValue() : -1, b.toString());
    }

    static void passes(boolean guard, String... scenarios) throws Exception {
        String[] args = new String[scenarios.length + 1];
        args[0] = guard ? "guard=on" : "guard=off";
        System.arraycopy(scenarios, 0, args, 1, scenarios.length);
        Out o = probe(guard ? List.of(GUARD) : List.of(), args);
        assertTrue(o.text().contains("CHECK " + guard + "\n"), "Graph.CHECK as wanted:\n" + o.text());
        assertFalse(o.text().contains("FAIL "), o.text());
        for (String s : scenarios) {
            if (!s.equals("run:all") && !s.equals("all-unit")) {
                assertTrue(o.text().contains("PASS " + s + "\n"), s + " passed:\n" + o.text());
            }
        }
        assertTrue(o.text().contains("DONE failed=0"), o.text());
        assertEquals(0, o.exit(), o.text());
    }

    // ---- the guard switch

    @Test
    void guard_isPropertyOnly_eaAloneDoesNotEnableIt() throws Exception {
        Out o = probe(List.of("-ea"), "guard=off", "basic");
        assertTrue(o.text().contains("CHECK false\n"), o.text());
        assertTrue(o.text().contains("PASS basic\n"), o.text());
        Out on = probe(List.of("-ea", GUARD), "guard=on", "basic");
        assertTrue(on.text().contains("CHECK true\n"), on.text());
        assertTrue(on.text().contains("PASS basic\n"), on.text());
    }

    // ---- item 3: sparse ids (basic uses ids 0, 3, 42, 1e9+7, Long.MAX_VALUE), unknown tids

    @Test
    void basic_sparseIds_handDerivedPrefixes_guardOn() throws Exception {
        passes(true, "basic");
    }

    @Test
    void unknownTid_inPrefix_false_guardOn() throws Exception {
        passes(true, "unknownTid");
    }

    // ---- item 4

    @Test
    void copyIsolation_guardOn() throws Exception {
        passes(true, "copyIsolation");
    }

    @Test
    void copyIsolation_guardOff() throws Exception {
        passes(false, "copyIsolation");
    }

    // ---- item 5: contracts

    @Test
    void restrictRemovingAncestor_reportedByGuard() throws Exception {
        passes(true, "restrictRemovesAncestor");
    }

    @Test
    void restrictRemovingAncestor_guardOff_staleClockDocumented() throws Exception {
        passes(false, "restrictRemovesAncestor");
    }

    @Test
    void setRfOrBottomWithSuccessor_reportedByGuard() throws Exception {
        passes(true, "setRfPoSuccessor", "setRfHbSuccessor", "setBottomSuccessor");
    }

    @Test
    void setRfOrBottomWithSuccessor_guardOff_staleClockDocumented() throws Exception {
        passes(false, "setRfPoSuccessor", "setRfHbSuccessor", "setBottomSuccessor");
    }

    // ---- item 6

    @Test
    void restrictRemovingRfSource_reportedByGuard() throws Exception {
        passes(true, "restrictRemovesRfSource");
    }

    @Test
    void restrictRemovingRfSource_guardOff_documented() throws Exception {
        passes(false, "restrictRemovesRfSource");
    }

    @Test
    void absentRfSource_reportedAtSetRf_guardOn() throws Exception {
        passes(true, "absentRfSource");
    }

    @Test
    void absentRfSource_thenAppended_guardOff() throws Exception {
        passes(false, "absentRfSource");
    }

    // ---- items 1, 2 and CD/MBOX: Must runs with an observer, guard on

    @Test
    void spawnJoinPrograms_guardOn() throws Exception {
        passes(true, "run:SJBackward", "run:SJRevisit", "run:SJChain", "run:SpawnAfterAwait", "run:SpawnBeforeJoin",
                "run:SpawnAfterJoin", "run:JoinChainSpawn", "run:JoinChainSpawnEarly", "run:ProcessSpawnsProcess",
                "run:ProcessAwaitsProcess");
    }

    @Test
    void backwardRevisitPrograms_guardOn() throws Exception {
        passes(true, "run:SSR-ex42", "run:NSR3", "run:SSRBR-ex43", "run:MustSection13", "run:NondetK3SSR",
                "run:NSRN4-receiverFirst", "run:NNR3-asyn", "run:SRnb", "run:BlockedAlone");
    }

    @Test
    void cdAndMbox_refused_guardOn() throws Exception {
        passes(true, "run:R3-cd-send", "run:R3-mbox-recv", "run:R3-cd-replay", "run:R3-p2p", "run:R3-asyn");
    }

    @Test
    void allPrograms_guardOff_sameDerivedCounts() throws Exception {
        passes(false, "run:all");
    }
}
