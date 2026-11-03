package org.mpi_sws.jmc.mp.sorted;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * SORTED-EXPORT round 01 (tester) fixture, P2P: "s" sends {@code S(x)} to "r", x chosen by a nondet among
 * {@code "～"} (index 0, UTF-8 {@code EF BD 9E}, UTF-16 {@code FF5E}) and {@code "😀"} (U+1F600,
 * index 1, UTF-8 {@code F0 9F 98 80}, UTF-16 {@code D83D DE00}); "r" receives it. Visible role: r. Two
 * graphs, two summaries that differ only in that string: in UTF-8 byte order the U+FF5E line comes first,
 * in UTF-16 order ({@link String#compareTo}) the U+1F600 line does.
 */
public final class SortedUnicode implements MpProgram {
    @Tag("S") record S(String x) implements MpMessage {}

    static final String BMP = "～";
    static final String ASTRAL = "😀";
    private static final CommunicationModel M = CommunicationModel.P2P;

    /** The launcher constructs a program without arguments. */
    public SortedUnicode() {}

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(M, S.class));
        MpProcess.Handle s = MpProcess.spawn("s", () -> Channel.send(M, r.tid(),
                new S(Nondet.chooseIndex(2) == 0 ? BMP : ASTRAL)));
        r.await();
        s.await();
    }

    @Override public String programName() { return "sorted-unicode"; }
    @Override public String size() { return "1"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("r", "s"); }
    @Override public List<String> visibleRoles() { return List.of("r"); }
}
