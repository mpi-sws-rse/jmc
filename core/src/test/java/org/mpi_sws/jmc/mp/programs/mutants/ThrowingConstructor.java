package org.mpi_sws.jmc.mp.programs.mutants;

/** Tester fixture (J0 round 02): a Runnable whose public no-arg constructor throws. */
public final class ThrowingConstructor implements Runnable {
    /** Throws. */
    public ThrowingConstructor() {
        throw new IllegalStateException("constructor fails");
    }

    @Override
    public void run() {}
}
