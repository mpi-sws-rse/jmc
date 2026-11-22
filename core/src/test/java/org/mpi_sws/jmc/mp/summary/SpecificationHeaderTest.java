package org.mpi_sws.jmc.mp.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The online check's reader compares the specification header's {@code model} with the program's and, in a
 * minor-1 header, its {@code roles} (the visible roles and the invisible roles a visible payload may name); a
 * minor-0 header (no {@code roles}) is read with its roles not compared. Each refusal has a test.
 */
class SpecificationHeaderTest {

    static final List<String> VIS = List.of("c0", "c1");
    static final SpecificationFile.Expected EXP =
            new SpecificationFile.Expected(VIS, "p2p", List.of("c0", "c1", "h"));
    static final String LINE = "{\"statuses\":{\"c0\":\"done\",\"c1\":\"done\"},\"words\":{\"c0\":[],\"c1\":[]},"
            + "\"counts\":[],\"n\":1}";

    static String header(String minorAndRoles, String model) {
        return "{\"format\":1" + minorAndRoles + ",\"tool\":\"traceforge\",\"program\":\"p\",\"size\":\"s\","
                + "\"visible\":[\"c0\",\"c1\"]" + (minorAndRoles.isEmpty() ? "" : ",\"roles\":[\"c0\",\"c1\",\"h\"]")
                + (model == null ? "" : ",\"model\":\"" + model + "\"")
                + ",\"enumeration\":\"complete\",\"encoding\":1}";
    }

    static Path file(String header) throws Exception {
        Path p = Files.createTempFile("spec-header", ".jsonl");
        Files.writeString(p, header + "\n" + LINE + "\n", StandardCharsets.UTF_8);
        return p;
    }

    static String refused(String header) throws Exception {
        Path p = file(header);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SpecificationFile.load(p, EXP),
                "not refused: " + header);
        return e.getMessage();
    }

    @Test
    void minor1_equalRolesAndModel_loadedAndCompared() throws Exception {
        SpecificationFile.Loaded l = SpecificationFile.load(file(header(",\"minor\":1", "p2p")), EXP);
        assertTrue(l.rolesCompared());
        assertEquals(List.of("c0", "c1", "h"), l.roles());
        assertEquals(1, l.index().size());
    }

    @Test
    void minor0_rolesNotCompared() throws Exception {
        SpecificationFile.Loaded l = SpecificationFile.load(file(header("", "p2p")), EXP);
        assertFalse(l.rolesCompared());
        assertNull(l.roles());
        assertEquals(1, l.index().size());
    }

    @Test
    void modelDiffers_refused() throws Exception {
        String m = refused(header(",\"minor\":1", "asyn"));
        assertTrue(m.contains("the two models differ: asyn and p2p"), m);
        String m0 = refused(header("", "cd"));
        assertTrue(m0.contains("the two models differ: cd and p2p"), m0);
    }

    @Test
    void modelMissing_refused() throws Exception {
        String m = refused(header("", null));
        assertTrue(m.contains("has no model"), m);
    }

    @Test
    void minor1_rolesDiffer_refused() throws Exception {
        String h = header(",\"minor\":1", "p2p")
                .replace("\"roles\":[\"c0\",\"c1\",\"h\"]", "\"roles\":[\"c0\",\"c1\"]");
        String m = refused(h);
        assertTrue(m.contains("has roles [c0, c1], the program [c0, c1, h]"), m);
    }

    @Test
    void minor1_rolesMissing_refused() throws Exception {
        String h = header(",\"minor\":1", "p2p").replace(",\"roles\":[\"c0\",\"c1\",\"h\"]", "");
        String m = refused(h);
        assertTrue(m.contains("the header has roles without minor or minor without roles"), m);
    }

    @Test
    void unknownMinor_refused() throws Exception {
        String m = refused(header(",\"minor\":2", "p2p"));
        assertTrue(m.contains("minor is 2; this reader knows minor 1"), m);
    }

    /** The JMC writer's own header is read back with its roles compared. */
    @Test
    void ownHeader_readBack() throws Exception {
        String h = SummaryExporter.header("p", "s", VIS, List.of("h", "c1", "c0"),
                org.mpi_sws.jmc.mp.events.CommunicationModel.P2P, "complete", null, null);
        SpecificationFile.Loaded l = SpecificationFile.load(file(h), EXP);
        assertTrue(l.rolesCompared());
        assertEquals(List.of("c0", "c1", "h"), l.roles());
    }
}
