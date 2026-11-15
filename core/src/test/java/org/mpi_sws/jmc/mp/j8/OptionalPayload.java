package org.mpi_sws.jmc.mp.j8;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;
import java.util.Optional;

/**
 * P2 round 01 (tester) fixture, export-capable (MpProgram), P2P; the visible role is {@code srv} only, so
 * every summary line has {@code "counts":[]}. Argument:
 *
 * <ul>
 *   <li>{@code visible}: "a" sends {@code Idle(0, Some(1), 2)} then {@code Res(Got(5, Some(7)))} to srv; "b"
 *       sends {@code Idle(0, None, 0)}, {@code Wrap(Some(Inner(3)))}, {@code Wrap(None)} to srv; srv receives
 *       five messages with an unguarded receive. P2P keeps each sender's order: C(5, 2) = 10 executions, one
 *       summary each (srv's word is the interleaving).</li>
 *   <li>{@code invisible}: "c" sends {@code V(1)} to srv; the invisible "x" sends {@code Hidden(None)} and then
 *       {@code Hidden(Some(Inner(2)))} or {@code Hidden(Some(Some(4)))} (a nondet, index 0 first) to the
 *       invisible "z", which receives both. Two executions, one summary (n 2); only invisible messages carry
 *       an Optional (the paxos / eokv case).</li>
 *   <li>{@code null}: "c" sends {@code Res(Got(5, null))} to srv: a visible payload with a null component at
 *       path {@code res.cas} of message {@code OptionalPayload$Msg$Res}.</li>
 * </ul>
 */
public final class OptionalPayload implements MpProgram {
    @Tag("Idle") record Idle(int node, Optional<Integer> sent, int got) implements MpMessage {}

    sealed interface Response {}

    @Tag("Got") record Got(int value, Optional<Integer> cas) implements Response {}

    sealed interface Msg extends MpMessage {
        @Tag("Res") record Res(Response res) implements Msg {}
    }

    @Tag("Inner") record Inner(int v) {}

    @Tag("Wrap") record Wrap(Optional<Inner> in) implements MpMessage {}

    @Tag("V") record V(int v) implements MpMessage {}

    @Tag("Hidden") record Hidden(Optional<Object> o) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;
    private final String mode;

    public OptionalPayload(String... args) {
        this.mode = args[0].trim();
        if (!List.of("visible", "invisible", "null").contains(mode)) {
            throw new IllegalArgumentException("mode visible|invisible|null, not " + mode);
        }
    }

    @Override
    public void run() {
        switch (mode) {
            case "visible" -> {
                MpProcess.Handle srv = MpProcess.spawn("srv", () -> {
                    for (int i = 0; i < 5; i++) {
                        Channel.recv(M);
                    }
                });
                MpProcess.Handle a = MpProcess.spawn("a", () -> {
                    Channel.send(M, srv.tid(), new Idle(0, Optional.of(1), 2));
                    Channel.send(M, srv.tid(), new Msg.Res(new Got(5, Optional.of(7))));
                });
                MpProcess.Handle b = MpProcess.spawn("b", () -> {
                    Channel.send(M, srv.tid(), new Idle(0, Optional.empty(), 0));
                    Channel.send(M, srv.tid(), new Wrap(Optional.of(new Inner(3))));
                    Channel.send(M, srv.tid(), new Wrap(Optional.empty()));
                });
                srv.await();
                a.await();
                b.await();
            }
            case "invisible" -> {
                MpProcess.Handle srv = MpProcess.spawn("srv", () -> Channel.recv(M, V.class));
                MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.send(M, srv.tid(), new V(1)));
                MpProcess.Handle z = MpProcess.spawn("z", () -> {
                    Channel.recv(M, Hidden.class);
                    Channel.recv(M, Hidden.class);
                });
                MpProcess.Handle x = MpProcess.spawn("x", () -> {
                    Channel.send(M, z.tid(), new Hidden(Optional.empty()));
                    Channel.send(M, z.tid(), new Hidden(Nondet.chooseIndex(2) == 0
                            ? Optional.<Object>of(new Inner(2)) : Optional.<Object>of(Optional.of(4))));
                });
                srv.await();
                c.await();
                z.await();
                x.await();
            }
            default -> {
                MpProcess.Handle srv = MpProcess.spawn("srv", () -> Channel.recv(M));
                MpProcess.Handle c = MpProcess.spawn("c", () -> Channel.send(M, srv.tid(),
                        new Msg.Res(new Got(5, null))));
                srv.await();
                c.await();
            }
        }
    }

    @Override public String programName() { return "j8-optional"; }
    @Override public String size() { return mode; }
    @Override public CommunicationModel model() { return M; }

    @Override
    public List<String> rolesInSpawnOrder() {
        return switch (mode) {
            case "visible" -> List.of("srv", "a", "b");
            case "invisible" -> List.of("srv", "c", "z", "x");
            default -> List.of("srv", "c");
        };
    }

    @Override public List<String> visibleRoles() { return List.of("srv"); }
}
