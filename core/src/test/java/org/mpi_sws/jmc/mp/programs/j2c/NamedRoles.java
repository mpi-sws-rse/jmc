package org.mpi_sws.jmc.mp.programs.j2c;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.MpThread;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.programs.j1.J1Fixture;

import java.util.List;

/**
 * J2c round 02 (owner's ruling Q-J2c-3, roles by name). Variants, all declaring roles {@code [a, b]}
 * (except {@code undeclared}): b receives once, a sends V(1) to b.
 * <ul>
 *   <li>{@code ab} / {@code ba}: spawned in the order a, b or b, a; same names, so the same summary;</li>
 *   <li>{@code norole}: b is created with {@code new MpThread(body)} (no role): refused;</li>
 *   <li>{@code undeclared}: declares {@code [a]} only, b is spawned with role b: refused;</li>
 *   <li>{@code twice}: a second process is also spawned as a (it sends nothing): refused;</li>
 *   <li>{@code missing}: declares {@code [a, b, c]}; c is never spawned: refused;</li>
 *   <li>{@code twice_hidden} / {@code missing_hidden}: an invisible declared role h, spawned twice /
 *       never: refused (only the declared-list check sees an invisible role).</li>
 * </ul>
 */
public final class NamedRoles extends J1Fixture {

    private final String variant;

    /** The {@code ab} variant. */
    public NamedRoles() {
        this("ab");
    }

    /**
     * A variant.
     *
     * @param variant ab, ba, norole, undeclared, twice or missing
     */
    public NamedRoles(String variant) {
        super("named-roles", CommunicationModel.P2P, variant.equals("undeclared") ? List.of("a")
                : variant.equals("missing") ? List.of("a", "b", "c")
                : variant.endsWith("_hidden") ? List.of("a", "b", "h") : List.of("a", "b"));
        this.variant = variant;
    }

    @Override
    public List<String> visibleRoles() {
        return variant.endsWith("_hidden") ? List.of("a", "b") : rolesInSpawnOrder();
    }

    @Override
    public void run() {
        // createAB (review J2c, Codex's note): a is created before b (every other variant creates b first)
        Tid[] bt = new Tid[1];
        MpThread a0 = variant.equals("createAB")
                ? new MpThread(() -> Channel.send(CommunicationModel.P2P, bt[0], new V(1)), "a") : null;
        MpThread b = variant.equals("norole") ? new MpThread(() -> Channel.recv(CommunicationModel.P2P))
                : new MpThread(() -> Channel.recv(CommunicationModel.P2P), "b");
        bt[0] = Tid.of(b.getTaskId());
        MpThread a = a0 != null ? a0
                : new MpThread(() -> Channel.send(CommunicationModel.P2P, bt[0], new V(1)), "a");
        if (variant.equals("ba")) {
            b.start();
            a.start();
        } else {
            a.start();
            b.start();
        }
        MpProcess.Handle extra = variant.equals("twice") ? MpProcess.spawn("a", () -> { }) : null;
        // the hidden variants declare an invisible role h: twice_hidden spawns it twice, missing_hidden never
        MpProcess.Handle h1 = variant.equals("twice_hidden") ? MpProcess.spawn("h", () -> { }) : null;
        MpProcess.Handle h2 = variant.equals("twice_hidden") ? MpProcess.spawn("h", () -> { }) : null;
        try {
            a.join1();
            b.join1();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        for (MpProcess.Handle x : new MpProcess.Handle[] {extra, h1, h2}) {
            if (x != null) {
                x.await();
            }
        }
    }
}
