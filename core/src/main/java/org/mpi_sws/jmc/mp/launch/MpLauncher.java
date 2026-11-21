package org.mpi_sws.jmc.mp.launch;

/**
 * The launcher's former name, kept for one release so that existing command lines keep working.
 *
 * @deprecated use {@link org.mpi_sws.jmc.mp.cli.MpLauncher}; this class only forwards to it.
 */
@Deprecated(since = "0.3.0", forRemoval = true)
public final class MpLauncher {

    private MpLauncher() {}

    /**
     * Runs {@link org.mpi_sws.jmc.mp.cli.MpLauncher#main} with the same arguments.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        org.mpi_sws.jmc.mp.cli.MpLauncher.main(args);
    }
}
