/**
 * Process-level parallel exploration for the Must strategy: the exploration tree is split into
 * independent items at a deterministic boundary of the sequential run, and each item is explored to
 * exhaustion on its own.
 *
 * <p>A <em>split run</em> runs the sequential {@link org.mpi_sws.jmc.strategies.must.MustStrategy}
 * until its exploration stack holds at least {@code K} entries after some finished iteration (the
 * <em>split boundary</em>; {@code R} iterations have finished, the stack holds {@code N} entries).
 * The entries are snapshotted, indexed {@code 0} (bottom) to {@code N − 1} (top), and each is
 * explored in a fresh exploration stack until that stack is empty. The sequential run is the root's
 * {@code R} iterations followed by the items in the order {@code N − 1} down to {@code 0}, because
 * an iteration reads only the entry it pops and the frozen process-name table.
 *
 * <p>{@link org.mpi_sws.jmc.strategies.must.split.ItemDriver} runs a split run inside one JVM;
 * {@link org.mpi_sws.jmc.strategies.must.split.TypedGraphEncoding} gives graphs and values a typed
 * byte encoding independent of the JVM, from which outcome digests and the replay-state
 * fingerprint ({@link org.mpi_sws.jmc.strategies.must.split.StackFingerprint}) are computed.
 */
package org.mpi_sws.jmc.strategies.must.split;
