package org.mpisws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark a test method or class to be replayed in the JMC model checker.
 * It can be applied to methods or classes.
 *
 * <p>When applied, it indicates that the annotated test should be executed in a replay mode,
 * allowing the JMC model checker to replay previously recorded execution.
 *
 * <p>To be used when a bug is encountered in the model checking process.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcReplay {}
