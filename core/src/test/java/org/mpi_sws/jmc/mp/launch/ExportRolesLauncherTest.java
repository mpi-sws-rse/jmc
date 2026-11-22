package org.mpi_sws.jmc.mp.launch;

import org.mpi_sws.jmc.mp.cli.MpLauncher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Through the launcher: the exported header's {@code minor} and {@code roles}, the online check's refusals of a
 * differing model and differing roles, its warning on a minor-0 specification, and the per-summary measurement
 * counters (empty when nothing is summarised).
 */
class ExportRolesLauncherTest {

    static final String LAUNCHER = "org.mpi_sws.jmc.mp.cli.MpLauncher";
    static final String PROGRAMS = "org.mpi_sws.jmc.mp.fixtures.roles.ExportRolePrograms:";
    static final String DIR = "build/test-results/export-roles-launch/";

    static MpLauncherTest.Out export(String program, Path out) throws Exception {
        Files.createDirectories(out.getParent());
        Files.deleteIfExists(out);
        return MpLauncherTest.launch(LAUNCHER, PROGRAMS + program, "must", "0", "1",
                DIR + "report-" + out.getFileName(), out.toString(), "120");
    }

    static MpLauncherTest.Out online(String program, Path spec, String tag) throws Exception {
        return MpLauncherTest.launch(LAUNCHER, PROGRAMS + program, "must", "0", "1", DIR + "report-online-" + tag,
                "-", "120", "--spec", spec.toString());
    }

    /** The measurement block's keys and values, in order. */
    static Map<String, String> block(String text) {
        Map<String, String> v = new LinkedHashMap<>();
        boolean in = false;
        for (String line : text.split("\n")) {
            if (line.equals(MpLauncher.MEASUREMENTS_MARKER)) {
                in = true;
            } else if (in && line.matches("[a-z_]+: .*|[a-z_]+:")) {
                int at = line.indexOf(':');
                v.put(line.substring(0, at), line.substring(at + 1).trim());
            }
        }
        return v;
    }

    @Test
    void export_headerHasMinorAndRoles() throws Exception {
        Path out = Path.of(DIR + "payload-h.jsonl");
        MpLauncherTest.Out o = export("payload_h,declared", out);
        assertEquals(0, o.exit(), o.text());
        String h = Files.readAllLines(out, StandardCharsets.UTF_8).get(0);
        assertEquals("{\"format\":1,\"minor\":1,\"tool\":\"jmc\",\"program\":\"roles-payload_h\",\"size\":\"\","
                + "\"visible\":[\"a\"],\"roles\":[\"a\",\"h\"],\"model\":\"p2p\",\"enumeration\":\"complete\","
                + "\"encoding\":1}", h);
    }

    @Test
    void export_payloadNamesUndeclaredInvisible_refused() throws Exception {
        MpLauncherTest.Out o = export("payload_h,undeclared", Path.of(DIR + "payload-h-undeclared.jsonl"));
        assertEquals(1, o.exit(), o.text());
        assertTrue(o.text().contains("payload names process h, an invisible role that is not declared"), o.text());
    }

    @Test
    void online_ownSpec_conformsWithRolesCompared() throws Exception {
        Path spec = Path.of(DIR + "own-spec.jsonl");
        assertEquals(0, export("payload_h,declared", spec).exit());
        MpLauncherTest.Out o = online("payload_h,declared", spec, "own");
        assertEquals(0, o.exit(), o.text());
        assertTrue(o.text().contains("verdict:    conforms"), o.text());
        assertFalse(o.text().contains("warning: specification"), o.text());
    }

    @Test
    void online_minor0Spec_warnsAndChecks() throws Exception {
        Path own = Path.of(DIR + "minor0-own.jsonl");
        assertEquals(0, export("payload_h,declared", own).exit());
        List<String> lines = Files.readAllLines(own, StandardCharsets.UTF_8);
        String h0 = lines.get(0).replace(",\"minor\":1", "").replace(",\"roles\":[\"a\",\"h\"]", "");
        Path spec = Path.of(DIR + "minor0-spec.jsonl");
        Files.write(spec, List.of(h0, lines.get(1)), StandardCharsets.UTF_8);
        MpLauncherTest.Out o = online("payload_h,declared", spec, "minor0");
        assertEquals(0, o.exit(), o.text());
        assertTrue(o.text().contains("warning: specification " + spec + " has no roles (format minor 0); its roles"
                + " were not compared with the program's [a, h]"), o.text());
        assertTrue(o.text().contains("verdict:    conforms"), o.text());
    }

    @Test
    void online_modelDiffers_refused() throws Exception {
        Path own = Path.of(DIR + "model-own.jsonl");
        assertEquals(0, export("payload_h,declared", own).exit());
        List<String> lines = Files.readAllLines(own, StandardCharsets.UTF_8);
        Path spec = Path.of(DIR + "model-spec.jsonl");
        Files.write(spec, List.of(lines.get(0).replace("\"model\":\"p2p\"", "\"model\":\"asyn\""), lines.get(1)),
                StandardCharsets.UTF_8);
        MpLauncherTest.Out o = online("payload_h,declared", spec, "model");
        assertEquals(2, o.exit(), o.text());
        assertTrue(o.text().contains("the two models differ: asyn and p2p"), o.text());
    }

    @Test
    void online_rolesDiffer_refused() throws Exception {
        Path own = Path.of(DIR + "roles-own.jsonl");
        assertEquals(0, export("payload_h,declared", own).exit());
        List<String> lines = Files.readAllLines(own, StandardCharsets.UTF_8);
        Path spec = Path.of(DIR + "roles-spec.jsonl");
        Files.write(spec, List.of(lines.get(0).replace("\"roles\":[\"a\",\"h\"]", "\"roles\":[\"a\"]"), lines.get(1)),
                StandardCharsets.UTF_8);
        MpLauncherTest.Out o = online("payload_h,declared", spec, "roles");
        assertEquals(2, o.exit(), o.text());
        assertTrue(o.text().contains("MpLauncher: pairing: C2: the two `roles` lists differ: [\"a\"] and"
                + " [\"a\", \"h\"]"), o.text());
    }

    /** The counting mode summarises nothing, so the five per-summary counters print empty. */
    @Test
    void measurements_countingMode_countersEmpty() throws Exception {
        MpLauncherTest.Out o = MpLauncherTest.launch(LAUNCHER, PROGRAMS + "witness", "must", "0", "1",
                DIR + "report-count", "-", "120");
        assertEquals(0, o.exit(), o.text());
        Map<String, String> v = block(o.text());
        for (String k : List.of("max_message_events", "max_graph_events", "mean_graph_events", "max_visible_events",
                "mean_visible_events")) {
            assertEquals("", v.get(k), "counting mode: " + k + " in " + v);
        }
    }

    /** An export prints the witness-line count of its one execution (15, as derived in ExportRolesTest). */
    @Test
    void measurements_export_countersFromSummarisedGraph() throws Exception {
        Path out = Path.of(DIR + "witness-export.jsonl");
        MpLauncherTest.Out o = export("witness", out);
        assertEquals(0, o.exit(), o.text());
        Map<String, String> v = block(o.text());
        assertEquals("15", v.get("max_graph_events"), v.toString());
        assertEquals("15.000", v.get("mean_graph_events"), v.toString());
        assertEquals("2", v.get("max_message_events"), v.toString());
        assertEquals("1", v.get("max_visible_events"), v.toString());
        assertEquals("1.000", v.get("mean_visible_events"), v.toString());
    }
}
