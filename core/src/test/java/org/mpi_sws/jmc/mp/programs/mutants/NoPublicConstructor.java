package org.mpi_sws.jmc.mp.programs.mutants;

/** Tester fixture (J0 round 01): a Runnable with no public no-argument constructor. */
public final class NoPublicConstructor implements Runnable {
    private NoPublicConstructor(int unused) {}

    @Override
    public void run() {}
}
