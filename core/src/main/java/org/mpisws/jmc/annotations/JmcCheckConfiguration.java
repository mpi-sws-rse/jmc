package org.mpisws.jmc.annotations;

import org.mpisws.jmc.strategies.RandomSchedulingStrategy;
import org.mpisws.jmc.strategies.SchedulingStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcCheckConfiguration {
    String strategy() default "random";

//    SchedulingStrategy customStrategy() default RandomSchedulingStrategy.class;

    int numIterations() default 10;

    boolean debug() default false;

    String reportPath() default "build/test-results/jmc-report";

    long seed() default 0;
}
