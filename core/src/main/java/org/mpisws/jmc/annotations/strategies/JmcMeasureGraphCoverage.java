package org.mpisws.jmc.annotations.strategies;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

/**
 * This annotation is used to configure the JMC graph coverage measurement for a test method or
 * class.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcMeasureGraphCoverage {}
