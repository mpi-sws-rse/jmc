package org.mpi_sws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JMC test that is expected to fail with an assertion error.
 *
 * <p>Consumed by {@code JmcMethodTestDescriptor.execute}: when present, a {@code JmcCheckerException}
 * whose cause is an {@code AssertionError} is treated as <em>success</em>, while any other error still
 * fails the test and a clean pass is itself turned into a failure. Parameterless; retained at runtime;
 * applicable to methods and types.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcExpectAssertionFailure {}
