package org.mpisws.jmc.annotations;

import org.junit.platform.commons.annotation.Testable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A mandatory annotation to mark a test method or class to be run with the JMC model checker. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Testable
public @interface JmcCheck {}
