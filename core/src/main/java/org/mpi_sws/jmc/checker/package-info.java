/**
 * Defines core classes of the JMC checker framework.
 *
 * <p>This package manages a model-checking run: {@code JmcModelChecker} drives the exploration loop,
 * {@code JmcCheckerConfiguration} (with its {@code Builder}) holds the run parameters, {@code
 * JmcModelCheckerReport} accumulates the outcome, and {@code JmcTestTarget} /
 * {@code JmcFunctionalTestTarget} abstract the program under test. The checker sits between the JUnit
 * integration (which invokes it via {@code JmcTestExecutor}) and the runtime/strategy that execute a
 * single program. Failures are surfaced through the {@code exceptions} sub-package.
 */
package org.mpi_sws.jmc.checker;
