package org.mpi_sws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a class from JMC bytecode instrumentation.
 *
 * <p>Applicable to <strong>classes only</strong> ({@code @Target(TYPE)}). Unlike the other JMC
 * annotations, it is read by the <em>instrumentation agent</em> rather than the checker: the agent's
 * {@code JmcIgnoreVisitor} detects it and returns the class unchanged, so no event-logging is
 * inserted. Useful (and required when writing custom strategies) for classes that must not be
 * rewritten. Parameterless; retained at runtime.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcIgnoreInstrumentation {
}
