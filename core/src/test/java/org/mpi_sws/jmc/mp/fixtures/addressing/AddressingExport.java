package org.mpi_sws.jmc.mp.fixtures.addressing;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.fixtures.export.J1Fixture;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * Fixture: a process addressed before it starts: an
 * export-valid program in which a process is addressed before it runs and a tid travels in a payload. Roles
 * {@code a}, {@code b} (visible); main creates a (m/0), then b (m/1), starts a, then b, awaits a, then b. b sends
 * {@code Req(b)} to a; a answers {@code V(1)} to the tid it received. One execution.
 * <ul>
 *   <li>{@code create}: with {@code MpProcess.create}/{@code Pending.start} (b's body captures a's Pending tid
 *       before a runs);</li>
 *   <li>{@code ctor}: the same with today's workaround (public constructors, tids from {@code getTaskId}).</li>
 * </ul>
 * The two raise the same event sequence (§1), so their summaries must be equal.
 *
 * <p>Arguments ({@code AddressingExport:<variant>}): {@code create} (the default) or {@code ctor}.
 */
public final class AddressingExport extends J1Fixture {

    /** A payload naming a task. */
    @Tag("Req")
    public record Req(Tid from) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final boolean create;

    /** The {@code create} variant. */
    public AddressingExport() {
        this("create");
    }

    /**
     * @param args the variant: {@code create} or {@code ctor}
     */
    public AddressingExport(String... args) {
        super("p4-addressing", M, List.of("a", "b"));
        String v = args.length == 0 ? "create" : args[0].trim();
        if (!List.of("create", "ctor").contains(v)) {
            throw new IllegalArgumentException("unknown variant " + v);
        }
        this.create = v.equals("create");
    }

    private static void aBody() {
        Req q = Channel.recv(M, Req.class);
        Channel.send(M, q.from(), new V(1));
    }

    @Override
    public void run() {
        if (create) {
            MpProcess.Pending a = MpProcess.create("a");
            MpProcess.Pending b = MpProcess.create("b");
            MpProcess.Handle ha = a.start(AddressingExport::aBody);
            MpProcess.Handle hb = b.start(() -> {
                Channel.send(M, a.tid(), new Req(b.tid()));
                Channel.recv(M, V.class);
            });
            ha.await();
            hb.await();
            return;
        }
        Tid[] ids = new Tid[2];
        MpThread a = new MpThread(AddressingExport::aBody, "a");
        MpThread b = new MpThread(() -> {
            Channel.send(M, ids[0], new Req(ids[1]));
            Channel.recv(M, V.class);
        }, "b");
        ids[0] = Tid.of(a.getTaskId(), "a");
        ids[1] = Tid.of(b.getTaskId(), "b");
        a.start();
        b.start();
        P4CharPrograms.join(a);
        P4CharPrograms.join(b);
    }
}
