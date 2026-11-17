package org.mpi_sws.jmc.mp.must;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.checker.JmcCheckerConfiguration;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.must.J2cTest.Rec;
import org.mpi_sws.jmc.mp.must.J2cTest.Run;
import org.mpi_sws.jmc.mp.programs.roles.ExportRolePrograms;
import org.mpi_sws.jmc.mp.summary.MpValueEncoder;
import org.mpi_sws.jmc.mp.summary.SpecificationFile;
import org.mpi_sws.jmc.mp.summary.SummaryExporter;
import org.mpi_sws.jmc.strategies.mp.MpRecordingStrategy;
import org.mpi_sws.jmc.strategies.must.MustStrategy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The export's header {@code minor} and {@code roles}, the role checks (non-empty ASCII, declared once, visible
 * roles listed once), the payload-nameable declaration and its refusal, Java enums as unit variants, daemons
 * started by a process other than main or after main's first await, the witness's {@code TJOIN}, daemon
 * marker and sender name, and the per-summary event-line count.
 * Every new refusal has a negative test here or in {@code SpecificationHeaderTest} / {@code ExportRolesLauncherTest}.
 */
class ExportRolesTest {

    static final String DIR = "build/test-results/export-roles/";

    static Run must(MpProgram p, String tag) {
        String path = DIR + "must-" + tag;
        MustStrategy s = new MustStrategy(1L, path, p.rolesInSpawnOrder(), p.visibleRoles(), p.payloadNameableRoles());
        return J2cTest.check(tag, J2cTest.main(p), new JmcCheckerConfiguration.Builder().strategyType("must")
                .strategyConstructor(c -> s).numIterations(10000).seed(1L).reportPath(path), s);
    }

    static Rec recorder(MpProgram p, String tag) {
        String path = DIR + "rec-" + tag;
        MpRecordingStrategy rec = new MpRecordingStrategy(1L, path, p.rolesInSpawnOrder(), p.visibleRoles(),
                p.payloadNameableRoles());
        return new Rec(rec, J2cTest.check(tag, J2cTest.main(p), new JmcCheckerConfiguration.Builder()
                .strategyType("mp-random").strategyConstructor(c -> rec).numIterations(5).seed(1L).reportPath(path),
                null));
    }

    static void ok(Run r) {
        assertNull(r.thrown(), r.failure());
        assertNull(r.report().getErrorMessage(), r.failure());
    }

    static Set<String> lines(Run r) {
        return J2cTest.lines(r.s().summaries());
    }

    // ------------------------------------------------------------------ header

    @Test
    void header_minor1_rolesAfterVisible() {
        String h = SummaryExporter.header("p", "s", List.of("b", "a"), List.of("h", "b", "a"), CommunicationModel.P2P,
                "complete", 1L, 3);
        assertEquals("{\"format\":1,\"minor\":1,\"tool\":\"jmc\",\"program\":\"p\",\"size\":\"s\","
                + "\"visible\":[\"a\",\"b\"],\"roles\":[\"a\",\"b\",\"h\"],"
                + "\"model\":\"p2p\",\"enumeration\":\"complete\",\"encoding\":1}", h);
    }

    @Test
    void headerRoles_visiblePlusPayloadNameable_sorted() {
        assertEquals(List.of("a", "h"), new ExportRolePrograms("payload_h", "declared").headerRoles());
        assertEquals(List.of("a"), new ExportRolePrograms("payload_h", "undeclared").headerRoles());
        assertEquals(List.of("a", "c", "h"), MustStrategy.headerRoles(List.of("c", "a"), List.of("h")));
    }

    // ------------------------------------------------------------------ role checks

    @Test
    void nonAsciiRole_refused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MustStrategy.checkDeclaredRole("clé"));
        assertTrue(e.getMessage().contains("is not ASCII"), e.getMessage());
        MustStrategy.checkDeclaredRole("c~1_x-Y.9");
    }

    @Test
    void nonAsciiRole_refusedByBothConstructors() {
        List<String> roles = List.of("a", "å");
        assertThrows(IllegalArgumentException.class, () -> new MustStrategy(1L, DIR + "x", roles, List.of("a")));
        assertThrows(IllegalArgumentException.class,
                () -> new MpRecordingStrategy(1L, DIR + "x", roles, List.of("a")));
    }

    @Test
    void duplicateVisibleRole_refused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new MustStrategy(1L, DIR + "x", List.of("a", "b"), List.of("a", "a")));
        assertTrue(e.getMessage().contains("visible roles are not distinct"), e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new MpRecordingStrategy(1L, DIR + "x", List.of("a", "b"), List.of("a", "a")));
    }

    @Test
    void payloadRoles_undeclared_refused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MustStrategy.checkExportRoles(List.of("a", "h"), List.of("a"), List.of("z")));
        assertTrue(e.getMessage().contains("payload-nameable role z is not declared"), e.getMessage());
    }

    @Test
    void payloadRoles_visible_refused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MustStrategy.checkExportRoles(List.of("a", "h"), List.of("a"), List.of("a")));
        assertTrue(e.getMessage().contains("payload-nameable role a is visible"), e.getMessage());
    }

    @Test
    void payloadRoles_duplicate_refused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MustStrategy.checkExportRoles(List.of("a", "h"), List.of("a"), List.of("h", "h")));
        assertTrue(e.getMessage().contains("payload-nameable roles are not distinct"), e.getMessage());
    }

    @Test
    void payloadRoles_withoutDeclaredRoles_refused() {
        assertThrows(IllegalArgumentException.class, () -> new MustStrategy(1L, DIR + "x", null, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new MustStrategy(1L, DIR + "x", List.of("a"), List.of("a"), null));
    }

    // ------------------------------------------------------------------ payload identities

    /** Derived: one execution; a's word is [{"s":{"t":"Who","f":["h"]}}]. */
    @Test
    void payloadNamesDeclaredInvisible_accepted_mustAndRecorder() {
        ExportRolePrograms p = new ExportRolePrograms("payload_h", "declared");
        Run m = must(p, "payload-h-declared");
        ok(m);
        Set<String> want = Set.of("{\"statuses\":{\"a\":\"done\"},"
                + "\"words\":{\"a\":[{\"s\":{\"t\":\"Who\",\"f\":[\"h\"]}}]},\"counts\":[]}");
        assertEquals(want, lines(m));
        Rec r = recorder(p, "payload-h-declared");
        ok(r.run());
        assertEquals(want, J2cTest.lines(r.rec().summaries()));
    }

    @Test
    void payloadNamesUndeclaredInvisible_refused_must() {
        Run m = must(new ExportRolePrograms("payload_h", "undeclared"), "payload-h-undeclared");
        J2cTest.fails(m, "payload names process h, an invisible role that is not declared payload-nameable");
        // A graph whose summary was refused counts in no measurement.
        assertEquals(0, m.s().distinctCompleteGraphs(), "refused graph not counted as complete");
        assertEquals(0, m.s().distinctBlockedGraphs(), "refused graph not counted as blocked");
        assertEquals(0, m.s().measurements().summarisedGraphs());
    }

    /** Only visible payloads need the declaration: an invisible message naming an undeclared role is exported. */
    @Test
    void invisibleMessageNamesUndeclaredInvisible_accepted() {
        Run m = must(new ExportRolePrograms("invisible_identity"), "invisible-identity");
        ok(m);
        assertEquals(Set.of("{\"statuses\":{\"a\":\"done\"},\"words\":{\"a\":[{\"s\":{\"t\":\"V\",\"f\":[1]}}]},"
                + "\"counts\":[]}"), lines(m));
    }

    /**
     * The witness prints invisible threads' sends too, and there an undeclared invisible role may be named: the
     * witness of the uncovered execution is written, showing h's send {@code Who(h)} to g.
     */
    @Test
    void witness_invisibleSendNamesUndeclaredRole_written() throws Exception {
        String w = witnessOf(new ExportRolePrograms("invisible_identity"), "invisible-identity");
        assertTrue(w.contains("SEND(g, {\"t\":\"Who\",\"f\":[\"h\"]})"), w);
    }

    @Test
    void payloadNamesUndeclaredInvisible_refused_recorder() {
        J2cTest.fails(recorder(new ExportRolePrograms("payload_h", "undeclared"), "payload-h-undeclared").run(),
                "payload names process h, an invisible role that is not declared payload-nameable");
    }

    @Test
    void payloadNamesMain_accepted_withoutDeclaration() {
        Run m = must(new ExportRolePrograms("payload_main"), "payload-main");
        ok(m);
        assertEquals(Set.of("{\"statuses\":{\"a\":\"done\"},"
                + "\"words\":{\"a\":[{\"s\":{\"t\":\"Who\",\"f\":[\"main\"]}}]},\"counts\":[]}"), lines(m));
    }

    // ------------------------------------------------------------------ daemons

    /** Derived: one execution; a receives V(7) from its daemon. Formerly refused ("was spawned by process"). */
    @Test
    void daemonStartedByProcess_accepted_mustAndRecorder() {
        ExportRolePrograms p = new ExportRolePrograms("daemon_by_a");
        Run m = must(p, "daemon-by-a");
        ok(m);
        Set<String> want = Set.of("{\"statuses\":{\"a\":\"done\"},\"words\":{\"a\":[{\"r\":{\"t\":\"V\",\"f\":[7]}}]},"
                + "\"counts\":[]}");
        assertEquals(want, lines(m));
        Rec r = recorder(p, "daemon-by-a");
        ok(r.run());
        assertEquals(want, J2cTest.lines(r.rec().summaries()));
    }

    /** Formerly refused ("was spawned after main's first await"). */
    @Test
    void daemonStartedAfterMainsFirstAwait_accepted_mustAndRecorder() {
        ExportRolePrograms p = new ExportRolePrograms("daemon_after_await");
        Run m = must(p, "daemon-after-await");
        ok(m);
        Set<String> want = Set.of("{\"statuses\":{\"a\":\"done\"},\"words\":{\"a\":[{\"s\":{\"t\":\"V\",\"f\":[1]}}]},"
                + "\"counts\":[]}");
        assertEquals(want, lines(m));
        Rec r = recorder(p, "daemon-after-await");
        ok(r.run());
        assertEquals(want, J2cTest.lines(r.rec().summaries()));
    }

    /** The exemption is for daemons only: a non-daemon started by a process stays refused. */
    @Test
    void nonDaemonStartedByProcess_stillRefused_mustAndRecorder() {
        ExportRolePrograms p = new ExportRolePrograms("child_by_a");
        J2cTest.fails(must(p, "child-by-a"), "was spawned by process");
        J2cTest.fails(recorder(p, "child-by-a").run(), "was spawned by process");
    }

    // ------------------------------------------------------------------ enums

    @Test
    void enumConstant_encodedAsUnitVariant() {
        MpValueEncoder e = new MpValueEncoder(t -> "x");
        assertEquals("{\"t\":\"RED\",\"f\":[]}", e.encode(ExportRolePrograms.Colour.RED));
        assertEquals("[{\"t\":\"GREEN\",\"f\":[]}]", e.encode(List.of(ExportRolePrograms.Colour.GREEN)));
    }

    @Test
    void enumPayload_exportedAsUnitVariant() {
        Run m = must(new ExportRolePrograms("enum_payload"), "enum-payload");
        ok(m);
        assertEquals(Set.of("{\"statuses\":{\"a\":\"done\"},\"words\":{\"a\":[{\"s\":{\"t\":\"RED\",\"f\":[]}}]},"
                + "\"counts\":[]}"), lines(m));
    }

    // ------------------------------------------------------------------ witness and event lines

    /** One uncovered execution's witness text, and the strategy that wrote it. */
    record Witnessed(String text, MustStrategy s) {}

    /** Runs {@code p} under the online check against a header-only specification and returns its one witness. */
    static Witnessed witnessed(ExportRolePrograms p, String tag) throws Exception {
        Path spec = Path.of(DIR + tag + "-spec.jsonl");
        Files.createDirectories(spec.getParent());
        Files.writeString(spec, SummaryExporter.header(p.programName(), p.size(), p.visibleRoles(), p.headerRoles(),
                p.model(), "complete", null, null) + "\n", StandardCharsets.UTF_8);
        String path = DIR + "must-witness-" + tag;
        MustStrategy s = new MustStrategy(1L, path, p.rolesInSpawnOrder(), p.visibleRoles(), p.payloadNameableRoles());
        SpecificationFile.Loaded loaded = SpecificationFile.load(spec, p.expected());
        assertTrue(loaded.rolesCompared());
        s.checkAgainst(loaded.index(), false);
        List<String> texts = new ArrayList<>();
        s.writeWitnesses(new MustStrategy.WitnessSink() {
            @Override
            public void uncovered(int iteration, String summaryLine, String witness) {
                texts.add(witness);
            }

            @Override
            public void refused(int iteration, String summaryLine, String why) {
                texts.add("REFUSED " + why);
            }
        });
        Run r = J2cTest.check(tag, J2cTest.main(p), new JmcCheckerConfiguration.Builder().strategyType("must")
                .strategyConstructor(c -> s).numIterations(10000).seed(1L).reportPath(path), s);
        ok(r);
        assertEquals(1, texts.size(), texts.toString());
        System.out.println("export-roles witness " + tag + "\n" + texts.get(0));
        return new Witnessed(texts.get(0), s);
    }

    static String witnessOf(ExportRolePrograms p, String tag) throws Exception {
        return witnessed(p, tag).text();
    }

    /**
     * The witness program against a header-only specification: its one execution is uncovered. Derived witness
     * (threads in creation order: main 0, h 1, a 2, the daemon 3; k from BEGIN 0): main creates h and a, then
     * joins a and h; a creates the daemon, then sends V(1) to h; h's receive reads a's send at (2, 2).
     */
    @Test
    void witness_tjoinDaemonMarkerSenderName_andEventLinesMatchM1() throws Exception {
        Witnessed x0 = witnessed(new ExportRolePrograms("witness"), "witness");
        String w = x0.text();
        MustStrategy s = x0.s();
        String body = w.substring(w.indexOf('\n') + 1);
        assertEquals("thread \"main\"[tid=0]:\n"
                + "\t(0, 0): BEGIN\n"
                + "\t(0, 1): TCREATE(1:\"h\")\n"
                + "\t(0, 2): TCREATE(2:\"a\")\n"
                + "\t(0, 3): TJOIN(2)\n"
                + "\t(0, 4): TJOIN(1)\n"
                + "\t(0, 5): END\n"
                + "thread \"h\"[tid=1]:\n"
                + "\t(1, 0): BEGIN\n"
                + "\t(1, 1): RECV() [(2, 2) a]\n"
                + "\t(1, 2): END\n"
                + "thread \"a\"[tid=2]:\n"
                + "\t(2, 0): BEGIN\n"
                + "\t(2, 1): TCREATE(3:\"" + daemonName(w) + "\")\n"
                + "\t(2, 2): SEND(h, {\"t\":\"V\",\"f\":[1]})\n"
                + "\t(2, 3): END\n"
                + "thread \"" + daemonName(w) + "\"[tid=3, daemon]:\n"
                + "\t(3, 0): BEGIN\n"
                + "\t(3, 1): END\n", body);
        long eventLines = body.lines().filter(l -> l.startsWith("\t")).count();
        assertEquals(15, eventLines, "main 6, h 3, a 4, the daemon 2");
        MustStrategy.Measurements x = s.measurements();
        assertEquals(1, x.summarisedGraphs());
        assertEquals(eventLines, x.maxGraphEvents(), "max_graph_events = the witness's event lines");
        assertEquals((double) eventLines, x.meanGraphEvents(), 0.0);
        assertEquals(2, x.maxMessageEvents(), "one send and one receive");
        assertEquals(1, x.maxVisibleEvents(), "a's one visible event");
    }

    /** The daemon's printed name (its internal Must name), taken from its TCREATE line. */
    private static String daemonName(String witness) {
        int at = witness.indexOf("TCREATE(3:\"");
        assertTrue(at >= 0, witness);
        int from = at + "TCREATE(3:\"".length();
        return witness.substring(from, witness.indexOf('"', from));
    }

    /** Without export nothing is summarised and the counters stay at zero (the launcher prints them empty). */
    @Test
    void measurements_countingMode_summarisesNothing() {
        MustStrategy s = new MustStrategy(1L, DIR + "count");
        Run r = J2cTest.check("count", J2cTest.main(new ExportRolePrograms("witness")),
                new JmcCheckerConfiguration.Builder()
                .strategyType("must").strategyConstructor(c -> s).numIterations(10000).seed(1L)
                .reportPath(DIR + "count"), s);
        ok(r);
        MustStrategy.Measurements x = s.measurements();
        assertEquals(0, x.summarisedGraphs());
        assertEquals(0, x.maxGraphEvents());
        assertEquals(0, x.maxMessageEvents(), "no message events counted without a summary");
        assertTrue(s.exhausted());
    }
}
