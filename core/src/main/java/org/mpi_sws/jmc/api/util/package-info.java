/**
 * Redefinitions of {@code java.util} classes with hooks to support JMC model checking.
 *
 * <p>Currently holds {@code JmcRandom}, a {@link java.util.Random} replacement whose {@code next}
 * reports a reactive random-value event and returns the value the strategy supplies. The
 * concurrency-specific replacements live in the {@code concurrent} sub-package.
 */
package org.mpi_sws.jmc.api.util;
