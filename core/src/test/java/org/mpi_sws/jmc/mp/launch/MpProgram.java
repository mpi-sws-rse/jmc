package org.mpi_sws.jmc.mp.launch;

import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/**
 * What a program must declare to be exported as compact summaries
 * (plan/jmc-must/SUMMARY-FORMAT.md): its name and size for the header, its communication model,
 * and its roles. No runtime event carries a role, so the roles are given here in <em>spawn
 * order</em> — the k-th process main spawns is task {@code k + 1} — and main must spawn every
 * process before any of them runs a message-passing event and await all of them.
 */
public interface MpProgram extends Runnable {

    /**
     * The program name, identical on both sides.
     *
     * @return the name
     */
    String programName();

    /**
     * The size parameters, as text.
     *
     * @return the size
     */
    String size();

    /**
     * The communication model every send and receive uses.
     *
     * @return the model
     */
    CommunicationModel model();

    /**
     * The declared roles: the name of every process main spawns with {@code MpProcess.spawn(role,
     * body)}. Since J2c (owner's ruling Q-J2c-3) a role is the spawn's name, not its position, and
     * this list is a check: the spawned roles must equal it, each exactly once, or the export is
     * refused. The order is irrelevant; the name is kept for compatibility.
     *
     * @return the roles, distinct
     */
    List<String> rolesInSpawnOrder();

    /**
     * The roles whose events are compared; by default all of them.
     *
     * @return the visible roles
     */
    default List<String> visibleRoles() {
        return rolesInSpawnOrder();
    }
}
