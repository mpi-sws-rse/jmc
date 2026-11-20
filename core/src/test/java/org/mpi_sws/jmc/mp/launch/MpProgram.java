package org.mpi_sws.jmc.mp.launch;

import org.mpi_sws.jmc.mp.events.CommunicationModel;

import java.util.List;

/**
 * What a program must declare to be exported as compact summaries (the summary format shared with
 * TraceForge): its name and size for the header, its communication model,
 * and its roles. A process's role is the name it is spawned or created with
 * ({@code MpProcess.spawn(role, body)}, {@code MpProcess.create(role)}); it travels on the creation
 * event, and the declared roles are a check, in any order. Main must start every process before it
 * awaits any, await all of them, and perform no send or receive.
 *
 * <p>To address a process, use its {@code Tid} from {@code Handle.tid()} or, before it runs,
 * {@code Pending.tid()} ({@code MpProcess.create}); do not compute task ids. What holds: in every
 * execution task ids restart at 1 (main); every runtime task allocation (every process or thread
 * constructed by any task, daemons included) takes the next id in allocation order, so the k-th
 * allocation, counting from k = 1, is task {@code k + 1}; ids are reused by the next execution.
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
     * body)} or creates with {@code MpProcess.create(role)}. A role is the spawn's name, not its
     * position, and this list is a check: the spawned roles must equal
     * it, each exactly once, or the export is refused. The order is irrelevant; the method's name is
     * historical (kept so that existing programs compile). Read it through {@link #declaredRoles()}.
     *
     * @return the roles, distinct, in any order
     */
    List<String> rolesInSpawnOrder();

    /**
     * The declared roles ({@link #rolesInSpawnOrder()} under its accurate name), in any order.
     *
     * @return the roles, distinct
     */
    default List<String> declaredRoles() {
        return rolesInSpawnOrder();
    }

    /**
     * The roles whose events are compared; by default all of them.
     *
     * @return the visible roles
     */
    default List<String> visibleRoles() {
        return declaredRoles();
    }

    /**
     * The invisible declared roles whose process identity ({@code Tid}) may appear in a visible
     * payload; empty by default. A visible payload may name main, a visible role or one of these;
     * naming any other process is refused at export. The header's {@code roles} are the visible roles and
     * these, and the online check refuses a specification whose {@code roles} differ. TraceForge's
     * optional {@code Program} field is the same declaration on the Rust side.
     *
     * @return the payload-nameable roles: declared, not visible, distinct
     */
    default List<String> payloadNameableRoles() {
        return List.of();
    }

    /**
     * What the online check compares a specification header with: the visible roles, the model and the
     * header roles.
     *
     * @return the expected header fields
     */
    default org.mpi_sws.jmc.mp.summary.SpecificationFile.Expected expected() {
        return new org.mpi_sws.jmc.mp.summary.SpecificationFile.Expected(visibleRoles(),
                org.mpi_sws.jmc.mp.summary.SummaryExporter.modelName(model()), headerRoles());
    }

    /**
     * The header's {@code roles}: the visible roles and the payload-nameable roles, sorted.
     *
     * @return the roles
     */
    default List<String> headerRoles() {
        return org.mpi_sws.jmc.strategies.must.MustStrategy.headerRoles(visibleRoles(), payloadNameableRoles());
    }
}
