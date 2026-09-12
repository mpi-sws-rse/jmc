/**
 * Defines a custom JUnit 5 engine for running JMC tests.
 *
 * <p>This package provides the JUnit Platform {@code TestEngine} that discovers and runs JMC tests.
 * {@code JmcTestEngine} implements the {@code TestEngine} SPI — discovering classes and methods
 * annotated for JMC and executing the resulting descriptor tree — and {@code JmcTestExecutor} bridges
 * a discovered test method to the JMC model checker. The engine is registered with the JUnit Platform
 * via the {@code META-INF/services/org.junit.platform.engine.TestEngine} service file. The descriptor
 * types it produces live in the sibling {@code descriptors} package.
 */
package org.mpi_sws.jmc.integrations.junit5.engine;
