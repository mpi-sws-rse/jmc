package org.mpi_sws.jmc.mp.programs.roles;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * Fixtures for the export's roles, payload identities, daemons, enums and witnesses. Each variant is an
 * exportable program under model {@code p2p}, one execution unless said otherwise; {@code h} is always invisible.
 * <ul>
 *   <li>{@code payload_h}: main starts {@code h} then {@code a} (visible), awaits both. {@code a} sends
 *       {@code Who(h)} (a payload naming the invisible {@code h}) to {@code h}, which receives it. Its
 *       payload-nameable roles are {@code [h]} with argument {@code declared}, none with {@code undeclared};</li>
 *   <li>{@code payload_main}: as {@code payload_h}, but {@code a} sends {@code Who(main)}; main is always
 *       nameable, with no payload-nameable roles;</li>
 *   <li>{@code daemon_by_a}: {@code a} starts a daemon that sends {@code V(7)} to {@code a}; {@code a} receives it
 *       (a daemon started by a process other than main);</li>
 *   <li>{@code daemon_after_await}: main starts {@code a}, awaits it, then starts a daemon with an empty body
 *       (a daemon started after main's first completed await); {@code a} sends {@code V(1)} to {@code h}
 *       and {@code h} is started before the await;</li>
 *   <li>{@code child_by_a}: {@code a} starts the non-daemon {@code c} (declared, invisible) and awaits it: still
 *       refused (only main starts non-daemon processes, and only main awaits);</li>
 *   <li>{@code enum_payload}: {@code a} sends the enum constant {@code Colour.RED} to {@code h}
 *       (encoded as a unit variant);</li>
 *   <li>{@code witness}: main starts {@code h} then {@code a}; {@code a} starts a daemon, sends {@code V(1)} to
 *       {@code h}; {@code h} receives it; main awaits {@code a}, then {@code h}. The witness shows the daemon
 *       marker, the receive's sender name and main's two {@code TJOIN} lines;</li>
 *   <li>{@code invisible_identity}: roles {@code h}, {@code g} (both invisible) and {@code a}; {@code a} sends
 *       {@code V(1)} to {@code h}; {@code h} receives it and sends {@code Who(h)} to {@code g}, which receives it.
 *       Only an invisible message names {@code h}, so nothing needs declaring payload-nameable.</li>
 * </ul>
 * Arguments ({@code ExportRolePrograms:<variant>[,declared|undeclared]}).
 */
public final class ExportRolePrograms implements MpProgram {

    /** A payload naming a process. */
    @Tag("Who")
    public record Who(Tid t) implements MpMessage {}

    /** An integer payload. */
    @Tag("V")
    public record V(int x) implements MpMessage {}

    /** A Java enum sent as a payload (encoded as a unit variant). */
    public enum Colour implements MpMessage { RED, GREEN }

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String variant;
    private final boolean declared;

    /**
     * Creates the fixture.
     *
     * @param args the variant, and for {@code payload_h} whether {@code h} is declared payload-nameable
     */
    public ExportRolePrograms(String... args) {
        this.variant = args[0].trim();
        this.declared = args.length < 2 || args[1].trim().equals("declared");
        if (!List.of("payload_h", "payload_main", "daemon_by_a", "daemon_after_await", "child_by_a", "enum_payload",
                "witness", "invisible_identity").contains(variant)) {
            throw new IllegalArgumentException("unknown variant " + variant);
        }
    }

    @Override
    public String programName() {
        return "roles-" + variant;
    }

    @Override
    public String size() {
        return "";
    }

    @Override
    public CommunicationModel model() {
        return M;
    }

    @Override
    public List<String> rolesInSpawnOrder() {
        return variant.equals("child_by_a") ? List.of("h", "a", "c")
                : variant.equals("invisible_identity") ? List.of("h", "g", "a") : List.of("h", "a");
    }

    @Override
    public List<String> visibleRoles() {
        return List.of("a");
    }

    @Override
    public List<String> payloadNameableRoles() {
        return variant.equals("payload_h") && declared ? List.of("h") : List.of();
    }

    @Override
    public void run() {
        switch (variant) {
            case "payload_h" -> payloadH();
            case "payload_main" -> payloadMain();
            case "daemon_by_a" -> daemonByA();
            case "daemon_after_await" -> daemonAfterAwait();
            case "child_by_a" -> childByA();
            case "enum_payload" -> enumPayload();
            case "witness" -> witness();
            case "invisible_identity" -> invisibleIdentity();
            default -> throw new IllegalStateException(variant);
        }
    }

    private static void payloadH() {
        MpProcess.Pending h = MpProcess.create("h");
        MpProcess.Handle hh = h.start(() -> Channel.recv(M, Who.class));
        MpProcess.Handle ha = MpProcess.spawn("a", () -> Channel.send(M, h.tid(), new Who(h.tid())));
        ha.await();
        hh.await();
    }

    private static void payloadMain() {
        Tid main = MpProcess.self();
        MpProcess.Pending h = MpProcess.create("h");
        MpProcess.Handle hh = h.start(() -> Channel.recv(M, Who.class));
        MpProcess.Handle ha = MpProcess.spawn("a", () -> Channel.send(M, h.tid(), new Who(main)));
        ha.await();
        hh.await();
    }

    private static void daemonByA() {
        MpProcess.Handle hh = MpProcess.spawn("h", () -> {});
        MpProcess.Handle ha = MpProcess.spawn("a", () -> {
            Tid a = MpProcess.self();
            MpProcess.spawnDaemon(() -> Channel.send(M, a, new V(7)));
            Channel.recv(M, V.class);
        });
        hh.await();
        ha.await();
    }

    private static void daemonAfterAwait() {
        MpProcess.Pending h = MpProcess.create("h");
        MpProcess.Handle hh = h.start(() -> Channel.recv(M, V.class));
        MpProcess.Handle ha = MpProcess.spawn("a", () -> Channel.send(M, h.tid(), new V(1)));
        ha.await();
        MpProcess.spawnDaemon(() -> {});
        hh.await();
    }

    private static void childByA() {
        MpProcess.Handle hh = MpProcess.spawn("h", () -> {});
        MpProcess.Handle ha = MpProcess.spawn("a", () -> MpProcess.spawn("c", () -> {}).await());
        hh.await();
        ha.await();
    }

    private static void enumPayload() {
        MpProcess.Pending h = MpProcess.create("h");
        MpProcess.Handle hh = h.start(() -> Channel.recv(M, Colour.class));
        MpProcess.Handle ha = MpProcess.spawn("a", () -> Channel.send(M, h.tid(), Colour.RED));
        ha.await();
        hh.await();
    }

    private static void witness() {
        MpProcess.Pending h = MpProcess.create("h");
        MpProcess.Handle hh = h.start(() -> Channel.recv(M, V.class));
        MpProcess.Handle ha = MpProcess.spawn("a", () -> {
            MpProcess.spawnDaemon(() -> {});
            Channel.send(M, h.tid(), new V(1));
        });
        ha.await();
        hh.await();
    }

    private static void invisibleIdentity() {
        MpProcess.Pending h = MpProcess.create("h");
        MpProcess.Pending g = MpProcess.create("g");
        MpProcess.Handle hh = h.start(() -> {
            Channel.recv(M, V.class);
            Channel.send(M, g.tid(), new Who(h.tid()));
        });
        MpProcess.Handle hg = g.start(() -> Channel.recv(M, Who.class));
        MpProcess.Handle ha = MpProcess.spawn("a", () -> Channel.send(M, h.tid(), new V(1)));
        ha.await();
        hh.await();
        hg.await();
    }
}
