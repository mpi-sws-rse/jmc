package org.mpi_sws.jmc.mp.fixtures.mutants;

/** Fixture: a Runnable with no public no-argument constructor. */
public final class NoPublicConstructor implements Runnable {
    private NoPublicConstructor(int unused) {}

    @Override
    public void run() {}
}
