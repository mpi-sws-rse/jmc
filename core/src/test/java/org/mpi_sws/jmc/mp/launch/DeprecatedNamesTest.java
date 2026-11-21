package org.mpi_sws.jmc.mp.launch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The launcher's and the program interface's former names ({@code org.mpi_sws.jmc.mp.launch.MpLauncher},
 * {@code org.mpi_sws.jmc.mp.launch.MpProgram}) still work: a program that implements the former interface, run by
 * the former launcher, exports the same file as under the current names.
 */
class DeprecatedNamesTest {

    private static final String DIR = "build/test-results/deprecated-names/";

    /**
     * A program written against the former interface: a client sends one message to a server.
     */
    @SuppressWarnings({"deprecation", "removal"})
    public static final class LegacyProgram implements MpProgram {

        /**
         * The one message.
         *
         * @param n its value
         */
        @Tag("Ping")
        public record Ping(int n) implements MpMessage {}

        /** Creates the program. */
        public LegacyProgram() {}

        @Override
        public void run() {
            MpProcess.Handle server = MpProcess.spawn("server", () -> Channel.recv(CommunicationModel.P2P));
            MpProcess.Handle client = MpProcess.spawn("client",
                    () -> Channel.send(CommunicationModel.P2P, server.tid(), new Ping(1)));
            server.await();
            client.await();
        }

        @Override
        public String programName() {
            return "legacy";
        }

        @Override
        public String size() {
            return "1";
        }

        @Override
        public CommunicationModel model() {
            return CommunicationModel.P2P;
        }

        @Override
        public List<String> rolesInSpawnOrder() {
            return List.of("server", "client");
        }
    }

    private static byte[] export(String launcher, String name) throws Exception {
        Path out = Path.of(DIR + name + ".jsonl");
        Files.deleteIfExists(out);
        MpLauncherTest.Out o = MpLauncherTest.launch(launcher, LegacyProgram.class.getName(), "must", "0", "1",
                DIR + "report-" + name, out.toString(), "60");
        assertEquals(0, o.exit(), "exit status\n" + o.text());
        return Files.readAllBytes(out);
    }

    @Test
    void formerLauncherAndInterfaceExportAsTheCurrentOnes() throws Exception {
        byte[] former = export("org.mpi_sws.jmc.mp.launch.MpLauncher", "former");
        byte[] current = export("org.mpi_sws.jmc.mp.cli.MpLauncher", "current");
        String[] lines = new String(former, StandardCharsets.UTF_8).split("\n", -1);
        assertTrue(lines[0].contains("\"enumeration\":\"complete\""), lines[0]);
        assertEquals(3, lines.length, "one header and one summary line, each ended by a newline");
        assertArrayEquals(current, former);
    }
}
