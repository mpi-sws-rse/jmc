/**
 * The public API of JMC (Java Model Checker): drop-in replacements for the JDK concurrency types
 * that report events to the runtime and yield.
 *
 * <p>This root package holds {@link org.mpi_sws.jmc.api.JmcObject} (monitor {@code wait}/{@code
 * notify} replacements and identity-method handlers). The sub-packages provide the rest: {@code
 * util} ({@code JmcRandom}), {@code util.concurrent} (the {@code java.lang.Thread} /
 * {@code java.util.concurrent} replacements — threads, locks, atomics, executors, futures),
 * {@code util.statements} ({@code JmcAssume}/{@code JmcAssert}), and {@code symbolic} (symbolic
 * execution). The instrumentation agent rewrites user code to use these types so that every
 * concurrency operation becomes an observable, controllable scheduling point.
 */
package org.mpi_sws.jmc.api;