/**
 * Defines annotations used by users of the JMC library.
 *
 * <p>These are the user-facing markers that configure a JMC run declaratively. {@code JmcCheck} and
 * {@code JmcCheckConfiguration} mark and parameterize a test; {@code JmcTimeout}, {@code JmcReplay},
 * {@code JmcExpectExecutions}, and {@code JmcExpectAssertionFailure} tune an individual test's
 * execution; and {@code JmcIgnoreInstrumentation} opts a class out of instrumentation. The JUnit test
 * engine and descriptors read these (reflectively, at runtime) to drive discovery and build the
 * checker configuration. Strategy-specific annotations live in the {@code strategies} sub-package.
 */
package org.mpi_sws.jmc.annotations;
