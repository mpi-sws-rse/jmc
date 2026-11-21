package example;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.cli.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/** A client sends Ping(n), n chosen nondeterministically; the server answers Pong(n). */
public final class PingPong implements MpProgram {
    @Tag("Ping") public record Ping(int n) implements MpMessage {}
    @Tag("Pong") public record Pong(int n) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        MpProcess.Pending client = MpProcess.create("client"); // its Tid exists before it runs
        MpProcess.Handle server = MpProcess.spawn("server", () -> {
            Ping p = Channel.recv(M, Ping.class);
            Channel.send(M, client.tid(), new Pong(p.n()));
        });
        MpProcess.Handle c = client.start(() -> {
            int n = Nondet.choose(List.of(1, 2));
            Channel.send(M, server.tid(), new Ping(n));
            Pong r = Channel.recv(M, Pong.class);
            assert r.n() == n : "the answer must echo the request";
        });
        server.await();
        c.await();
    }

    @Override public String programName() { return "pingpong"; }
    @Override public String size() { return "1"; }
    @Override public CommunicationModel model() { return M; }
    @Override public List<String> rolesInSpawnOrder() { return List.of("server", "client"); }
}
