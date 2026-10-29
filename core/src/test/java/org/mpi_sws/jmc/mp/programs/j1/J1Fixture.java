package org.mpi_sws.jmc.mp.programs.j1;

import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.launch.MpProgram;
import org.mpi_sws.jmc.mp.summary.Tag;

import java.util.List;

/**
 * Tester fixtures for J1 round 01 (plan/jmc-must/log/dev/J1/round-01.prompt.md): a common base
 * holding the program name, model and roles, and the one payload record {@code V}.
 */
public abstract class J1Fixture implements MpProgram {

    /**
     * The payload; encoded {@code {"t":"V","f":[x]}}.
     *
     * @param x the integer carried
     */
    @Tag("V")
    public record V(int x) implements MpMessage {}

    private final String name;
    private final CommunicationModel model;
    private final List<String> roles;

    /**
     * Creates the fixture.
     *
     * @param name program name
     * @param model the model
     * @param roles roles in spawn order
     */
    protected J1Fixture(String name, CommunicationModel model, List<String> roles) {
        this.name = name;
        this.model = model;
        this.roles = roles;
    }

    @Override
    public String programName() {
        return name;
    }

    @Override
    public String size() {
        return "";
    }

    @Override
    public CommunicationModel model() {
        return model;
    }

    @Override
    public List<String> rolesInSpawnOrder() {
        return roles;
    }
}
