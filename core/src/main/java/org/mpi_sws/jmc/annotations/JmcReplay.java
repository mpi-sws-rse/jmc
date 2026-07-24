package org.mpi_sws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JMC test to run in <em>replay</em> mode.
 *
 * <p>When present, the test reproduces a previously recorded execution instead of exploring: {@code
 * JmcMethodTestDescriptor} sets its {@code isReplayTest} flag from this annotation and routes the run
 * to {@code JmcModelChecker.replay} (via {@code JmcTestExecutor.executeReplay}) rather than {@code
 * check}. Intended for temporary use after a bug is found, to replay the exact schedule that led to
 * it. Parameterless; retained at runtime; applicable to methods and types.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcReplay {}
