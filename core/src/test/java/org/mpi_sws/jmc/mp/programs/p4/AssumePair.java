package org.mpi_sws.jmc.mp.programs.p4;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * P4 tester fixture (plan/jmc-must/P4-PROPOSAL.md rev 7, §3 "Equivalence with today's workaround", §4): the
 * assume/workaround pair. Roles {@code s1}, {@code s2}, {@code s3}, {@code r}, all visible; main creates them in
 * this order (s1 m/0 ... r m/3), starts all, then awaits s1, s2, s3, r. Each {@code sk} sends {@code V(k)} to r.
 * r receives up to three times; when it reads {@code V(3)} on its first or second receive it stops there:
 * <ul>
 *   <li>{@code recv}: with today's workaround, a blocking receive whose guard accepts nothing;</li>
 *   <li>{@code assume}: with {@code MpProcess.assume(false)} at the same program position;</li>
 *   <li>{@code none}: it does not stop (reads all three).</li>
 * </ul>
 * Derived (before running): all sends precede r's receives under Must's lowest-id-first schedule, so the
 * executions are r's read orders: {@code recv}/{@code assume} 5 graphs (V(3) last: 2 complete; V(3) first: 1
 * blocked; V(3) second: 2 blocked), {@code none} 6 complete. A blocked r makes main's await of r block, so
 * main's Block is the last event and the export's never-awaited rule is exempt ({@code mainBlockedOnAwait}).
 * The program name and size are the same for every variant, so the exports of {@code recv} and {@code assume}
 * can be compared byte for byte.
 *
 * <p>Arguments ({@code AssumePair:<variant>}): the variant; no argument means {@code assume}.
 */
public final class AssumePair extends J1Fixture {

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String variant;

    /** The {@code assume} variant. */
    public AssumePair() {
        this("assume");
    }

    /**
     * @param args the variant: {@code recv}, {@code assume} or {@code none}
     */
    public AssumePair(String... args) {
        super("p4-assume-pair", M, List.of("s1", "s2", "s3", "r"));
        this.variant = args.length == 0 ? "assume" : args[0].trim();
        if (!List.of("recv", "assume", "none").contains(variant)) {
            throw new IllegalArgumentException("unknown variant " + variant);
        }
    }

    private void stop() {
        if (variant.equals("assume")) {
            MpProcess.assume(false);
        } else if (variant.equals("recv")) {
            Channel.recv(M, m -> false);
        }
    }

    @Override
    public void run() {
        MpProcess.Pending s1 = MpProcess.create("s1");
        MpProcess.Pending s2 = MpProcess.create("s2");
        MpProcess.Pending s3 = MpProcess.create("s3");
        MpProcess.Pending r = MpProcess.create("r");
        Tid rt = r.tid();
        MpProcess.Handle h1 = s1.start(() -> Channel.send(M, rt, new V(1)));
        MpProcess.Handle h2 = s2.start(() -> Channel.send(M, rt, new V(2)));
        MpProcess.Handle h3 = s3.start(() -> Channel.send(M, rt, new V(3)));
        MpProcess.Handle hr = r.start(() -> {
            for (int i = 0; i < 3; i++) {
                V v = Channel.recv(M, V.class);
                if (v.x() == 3 && i < 2) {
                    stop();
                    if (!variant.equals("none")) {
                        throw new AssertionError("a statement after the stop ran");
                    }
                }
            }
        });
        h1.await();
        h2.await();
        h3.await();
        hr.await();
    }
}
