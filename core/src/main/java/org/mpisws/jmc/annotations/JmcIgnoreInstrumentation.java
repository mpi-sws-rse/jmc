package org.mpisws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark methods or classes that should be ignored by the JMC instrumentation.
 * It can be applied to classes only.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcIgnoreInstrumentation {
}
