/**
 * Defines additional statements of the JMC API to be used in JMC tests.
 *
 * <p>These statements interact with the JMC runtime to steer or check an execution: {@code JmcAssume}
 * restricts exploration to schedules satisfying a precondition (a failed assumption prunes the
 * execution), and {@code JmcAssert} checks assertions (a failed assertion is the checker's
 * bug-found path), including symbolic-formula variants.
 */
package org.mpi_sws.jmc.api.util.statements;
