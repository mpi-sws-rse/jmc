package org.mpi_sws.jmc.mp.launch;

/**
 * The program interface's former name, kept for one release so that existing programs compile and run unchanged.
 *
 * @deprecated implement {@link org.mpi_sws.jmc.mp.cli.MpProgram}; this interface adds nothing to it.
 */
@Deprecated(since = "0.3.0", forRemoval = true)
public interface MpProgram extends org.mpi_sws.jmc.mp.cli.MpProgram {
}
