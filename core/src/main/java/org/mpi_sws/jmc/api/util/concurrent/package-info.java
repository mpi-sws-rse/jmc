/**
 * Redefinitions of {@code java.lang.Thread} and {@code java.util.concurrent} classes for JMC.
 *
 * <p>These are the replacement types the instrumentation agent swaps in so that concurrency
 * operations report events and yield: {@code JmcThread}/{@code JmcThreadFactory} (threads), {@code
 * JmcReentrantLock} and {@code JmcLockSupport} (locks/parking), the {@code JmcAtomic*} family
 * (atomics), and the executor/future types ({@code JmcExecutors}, {@code JmcExecutorService},
 * {@code JmcThreadPoolExecutor}, {@code JmcScheduledExecutorService}, {@code JmcFuture},
 * {@code JmcScheduledFuture}, {@code JmcCompletableFuture}). Each mimics its JDK counterpart but hooks
 * into the runtime so the scheduler can control the interleaving.
 */
package org.mpi_sws.jmc.api.util.concurrent;
