/**
 * The compact summary of a complete execution graph (summary format version 1): per visible role
 * the word of send and receive observations, the status read from the thread's last graph event,
 * and the causal order of the visible events as counts. The enumeration of a Java program is
 * compared with that of the corresponding Rust program in TraceForge through these summaries.
 *
 * <p>Pure: {@link org.mpi_sws.jmc.mp.summary.SummaryExporter} turns a graph into a
 * {@link org.mpi_sws.jmc.mp.summary.Summary} and a {@link org.mpi_sws.jmc.mp.summary.Summary}
 * into one JSON line; {@link org.mpi_sws.jmc.mp.summary.MpValueEncoder} renders values in the
 * canonical encoding both sides compare by bytes. Nothing here performs I/O or knows the runtime;
 * the strategy that holds the graphs collects the lines and the launcher writes the file.
 */
package org.mpi_sws.jmc.mp.summary;
