package org.mpi_sws.jmc.mp.j8;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;

import java.util.List;

/**
 * J8 test-3 fixture (fingerprint across JVMs, counting mode): enum and nested-record payloads.
 * r receives two messages; a sends {@code Cmd(op, Key("x", 1), [1, 2])} with op chosen by a nondet
 * (index 0 = PUT, 1 = GET); b sends the enum constant {@code Op.GET} itself.
 */
public final class EnumNested implements Runnable {
    enum Op implements MpMessage { PUT, GET }

    record Key(String k, int shard) {}

    record Cmd(Op op, Key key, List<Integer> vals) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    @Override
    public void run() {
        MpProcess.Handle r = MpProcess.spawn("r", () -> {
            Channel.recv(M);
            Channel.recv(M);
        });
        MpProcess.Handle a = MpProcess.spawn("a", () -> {
            Op op = Nondet.chooseIndex(2) == 0 ? Op.PUT : Op.GET;
            Channel.send(M, r.tid(), new Cmd(op, new Key("x", 1), List.of(1, 2)));
        });
        MpProcess.Handle b = MpProcess.spawn("b", () -> Channel.send(M, r.tid(), Op.GET));
        r.await();
        a.await();
        b.await();
    }
}
