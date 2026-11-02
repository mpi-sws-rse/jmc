package org.mpi_sws.jmc.mp.j8s;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * J8S round 01 (tester) fixture, export-capable (MpProgram), P2P. "t" sends Stop(k), k chosen by a nondet (0 first), to the server "srv";
 * srv waits for Stop, then sends Go to "a" and receives {@code n + 1} values; "a" waits for Go and sends
 * V(1) … V(n) to srv; "b" sends V(10) or V(20) (a nondet, index 0 = 10) to srv. Because srv's value receives are in the graph before a's
 * sends run, a's sends create backward revisits, and a revisit to a's later send while an earlier one is
 * unread is p2p-inconsistent: at K = 1 some items are zero-iteration (derived from the observation, see the
 * J8S round-01 report). Visible role: srv.
 */
public final class OnlineBank implements MpProgram {
    @Tag("V") record V(int v) implements MpMessage {}
    @Tag("Stop") record Stop(int k) implements MpMessage {}
    @Tag("Go") record Go() implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;
    private final int n;

    public OnlineBank(String... args) {
        this.n = Integer.parseInt(args[0].trim());
    }

    @Override
    public void run() {
        // srv needs a's Tid and a needs srv's: a holder filled before any process runs (processes run only
        // once main blocks in its first await).
        MpProcess.Handle[] holder = new MpProcess.Handle[1];
        MpProcess.Handle srv = MpProcess.spawn("srv", () -> {
            Channel.recv(M, Stop.class);
            Channel.send(M, holder[0].tid(), new Go());
            for (int i = 0; i <= n; i++) {
                Channel.recv(M, V.class);
            }
        });
        MpProcess.Handle a = MpProcess.spawn("a", () -> {
            Channel.recv(M, Go.class);
            for (int i = 1; i <= n; i++) {
                Channel.send(M, srv.tid(), new V(i));
            }
        });
        holder[0] = a;
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.send(M, srv.tid(),
                new V(Nondet.chooseIndex(2) == 0 ? 10 : 20)));
        MpProcess.Handle t = MpProcess.spawn("t", () -> Channel.send(M, srv.tid(), new Stop(Nondet.chooseIndex(2))));
        srv.await();
        a.await();
        b.await();
        t.await();
    }

    @Override public String programName() { return "j8s-online-bank"; }
    @Override public String size() { return "n" + n; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("srv", "a", "b", "t"); }
    @Override public List<String> visibleRoles() { return List.of("srv"); }
}
