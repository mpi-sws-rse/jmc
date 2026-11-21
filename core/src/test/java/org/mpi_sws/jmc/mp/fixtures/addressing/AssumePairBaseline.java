package org.mpi_sws.jmc.mp.fixtures.addressing;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.fixtures.export.J1Fixture;

import java.util.List;

/**
 * Fixture: {@link AssumePair}'s {@code recv} and {@code none} variants written with the API from before
 * process creation and assume only (the workaround: construct every {@code MpThread} with its role, then start
 * all, then await all), so that it compiles and runs on the baseline. Its event sequence is the one
 * {@code AssumePair} raises with {@code create}/{@code start} (create-all-then-start-all is the workaround's
 * sequence), and its program name and size are {@code AssumePair}'s, so its export md5 on the baseline is the
 * oracle for {@code AssumePair:recv} and {@code AssumePair:assume} after the change.
 *
 * <p>Arguments ({@code AssumePairBaseline:<variant>}): {@code recv} (the default) or {@code none}.
 */
public final class AssumePairBaseline extends J1Fixture {

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final boolean stops;

    /** The {@code recv} variant. */
    public AssumePairBaseline() {
        this("recv");
    }

    /**
     * @param args the variant: {@code recv} or {@code none}
     */
    public AssumePairBaseline(String... args) {
        super("p4-assume-pair", M, List.of("s1", "s2", "s3", "r"));
        String v = args.length == 0 ? "recv" : args[0].trim();
        if (!List.of("recv", "none").contains(v)) {
            throw new IllegalArgumentException("unknown variant " + v);
        }
        this.stops = v.equals("recv");
    }

    @Override
    public void run() {
        Tid[] rt = new Tid[1];
        MpThread s1 = new MpThread(() -> Channel.send(M, rt[0], new V(1)), "s1");
        MpThread s2 = new MpThread(() -> Channel.send(M, rt[0], new V(2)), "s2");
        MpThread s3 = new MpThread(() -> Channel.send(M, rt[0], new V(3)), "s3");
        MpThread r = new MpThread(() -> {
            for (int i = 0; i < 3; i++) {
                V v = Channel.recv(M, V.class);
                if (v.x() == 3 && i < 2 && stops) {
                    Channel.recv(M, m -> false);
                    throw new AssertionError("a statement after the stop ran");
                }
            }
        }, "r");
        rt[0] = Tid.of(r.getTaskId(), "r");
        s1.start();
        s2.start();
        s3.start();
        r.start();
        for (MpThread t : new MpThread[] {s1, s2, s3, r}) {
            P4CharPrograms.join(t);
        }
    }
}
