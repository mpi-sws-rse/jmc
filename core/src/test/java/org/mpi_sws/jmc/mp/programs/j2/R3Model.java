package org.mpi_sws.jmc.mp.programs.j2;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2 round 03, M3: NS+R with N = 2 (r spawned first, receives once; s1, s2 send V(1), V(2)) under the
 * models of a variant.
 *
 * <ul>
 *   <li>{@code cd-send}: r receives under p2p, s1 sends under CD, s2 under p2p;</li>
 *   <li>{@code mbox-recv}: r receives under MBOX, both send under MBOX;</li>
 *   <li>{@code cd-replay}: all p2p in the first run; from the second run on s2 sends under CD;</li>
 *   <li>{@code asyn}: all asyn (control); {@code p2p}: all p2p (control).</li>
 * </ul>
 */
public final class R3Model extends J1Fixture {
    private final String variant;
    private int runs;

    /** The {@code cd-send} variant. */
    public R3Model() {
        this("cd-send");
    }

    /**
     * The variant.
     *
     * @param variant one of cd-send, mbox-recv, cd-replay, asyn, p2p
     */
    public R3Model(String variant) {
        super("r3-model-" + variant, variant.equals("asyn") ? CommunicationModel.ASYN : CommunicationModel.P2P,
                List.of("r", "s1", "s2"));
        this.variant = variant;
    }

    @Override
    public void run() {
        boolean later = runs++ > 0;
        CommunicationModel rm;
        CommunicationModel m1;
        CommunicationModel m2;
        switch (variant) {
            case "cd-send" -> {
                rm = CommunicationModel.P2P;
                m1 = CommunicationModel.CD;
                m2 = CommunicationModel.P2P;
            }
            case "mbox-recv" -> {
                rm = CommunicationModel.MBOX;
                m1 = CommunicationModel.MBOX;
                m2 = CommunicationModel.MBOX;
            }
            case "cd-replay" -> {
                rm = CommunicationModel.P2P;
                m1 = CommunicationModel.P2P;
                m2 = later ? CommunicationModel.CD : CommunicationModel.P2P;
            }
            case "asyn" -> {
                rm = CommunicationModel.ASYN;
                m1 = CommunicationModel.ASYN;
                m2 = CommunicationModel.ASYN;
            }
            default -> {
                rm = CommunicationModel.P2P;
                m1 = CommunicationModel.P2P;
                m2 = CommunicationModel.P2P;
            }
        }
        MpProcess.Handle r = MpProcess.spawn("r", () -> Channel.recv(rm));
        Tid rt = r.tid();
        MpProcess.Handle s1 = MpProcess.spawn("s1", () -> Channel.send(m1, rt, new V(1)));
        MpProcess.Handle s2 = MpProcess.spawn("s2", () -> Channel.send(m2, rt, new V(2)));
        r.await();
        s1.await();
        s2.await();
    }
}
