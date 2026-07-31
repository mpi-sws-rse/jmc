package org.mpi_sws.jmc.annotations;

import org.junit.platform.commons.annotation.Testable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method or class to be run with the JMC model checker.
 *
 * <p>This is a parameterless <em>discovery marker</em>: the {@code JmcTestEngine} treats a class or
 * method carrying it (or {@link JmcCheckConfiguration}) as a JMC test (its {@code
 * IS_JMC_TEST_CONTAINER} predicate). It is meta-annotated with JUnit's {@link Testable} so IDEs offer
 * to run it, retained at runtime for reflective discovery, and applicable to both methods and types.
 * A test annotated only with {@code JmcCheck} runs with the default configuration; pair it with {@link
 * JmcCheckConfiguration} to set parameters.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Testable
public @interface JmcCheck {}
