package org.mpi_sws.jmc.mp.witness;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.cli.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * WITNESS tester fixture (P2P) with an unusual role name, chosen by the argument:
 * {@code nl} — the invisible sender is named {@code "c\nd"} (a line feed); {@code ls} — named
 * {@code "c\u2028d"} (LINE SEPARATOR); {@code del} — named {@code "c\u007fd"} (DEL); {@code csi}, {@code nel},
 * {@code ps} — named with U+009B (CSI, a C1 control), U+0085 (NEL), U+2029 (PARAGRAPH SEPARATOR) between c and d;
 * {@code nlcsi} — named c, a line feed, U+009B, d; {@code quote} — the visible receiver is named {@code s"v,w}
 * (a quote and a comma) and the sender {@code c}. The sender sends A(x), x a nondet of 2, to the
 * receiver, which receives one A. Two executions, two summaries. Visible: the receiver.
 */
public final class WitnessOddRole implements MpProgram {
    @Tag("A") record A(int v) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;
    private final String receiver;
    private final String sender;

    public WitnessOddRole(String... args) {
        switch (args[0].trim()) {
            case "nl" -> { receiver = "srv"; sender = "c\nd"; }
            case "ls" -> { receiver = "srv"; sender = "c\u2028d"; }
            case "del" -> { receiver = "srv"; sender = "c\u007fd"; }
            case "csi" -> { receiver = "srv"; sender = "c\u009bd"; }
            case "nel" -> { receiver = "srv"; sender = "c\u0085d"; }
            case "ps" -> { receiver = "srv"; sender = "c\u2029d"; }
            case "nlcsi" -> { receiver = "srv"; sender = "c\n\u009bd"; }
            case "quote" -> { receiver = "s\"v,w"; sender = "c"; }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn(receiver, () -> Channel.recv(M, A.class));
        MpProcess.Handle s = MpProcess.spawn(sender, () -> Channel.send(M, r.tid(), new A(Nondet.chooseIndex(2))));
        r.await();
        s.await();
    }

    @Override public String programName() { return "witness-odd-role"; }
    @Override public String size() { return "1"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of(receiver, sender); }
    @Override public List<String> visibleRoles() { return List.of(receiver); }
}
