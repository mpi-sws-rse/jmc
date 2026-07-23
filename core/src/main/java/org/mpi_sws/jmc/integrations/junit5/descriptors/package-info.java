/**
 * Defines JUnit 5 descriptors for JMC tests.
 *
 * <p>This package contains the descriptor tree that the JMC {@code TestEngine} builds during
 * discovery and walks during execution: {@code JmcEngineDescriptor} (the root), {@code
 * JmcClassTestDescriptor} (one container per JMC test class), and {@code JmcMethodTestDescriptor}
 * (one runnable leaf per test method). The {@code JmcExecutableTestDescriptor} interface marks the
 * runnable leaves, and {@code JmcDescriptorUtil} translates strategy-related annotations into the
 * checker configuration.
 *
 * <p>Together these descriptors define and execute JMC checks within the JUnit 5 test lifecycle,
 * integrating the JMC model checker into the JUnit 5 testing framework.
 */
package org.mpi_sws.jmc.integrations.junit5.descriptors;
