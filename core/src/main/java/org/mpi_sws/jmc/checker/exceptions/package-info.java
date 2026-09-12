/**
 * Defines exceptions used in the JMC Checker module.
 *
 * <p>{@code JmcCheckerException} is the checked base type callers handle; {@code
 * JmcInvalidConfigurationException} and {@code JmcCheckerTimeoutException} narrow it. {@code
 * JmcUnsupportedFeatureException} is the odd one out — an unchecked {@code RuntimeException} raised by
 * the instrumentation agent. (The runtime's own control-flow exceptions — {@code HaltTaskException},
 * {@code HaltExecutionException}, {@code HaltCheckerException} — live in the {@code runtime} package,
 * not here.)
 */
package org.mpi_sws.jmc.checker.exceptions;
