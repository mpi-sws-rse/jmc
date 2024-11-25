package org.mpisws.annotations;

import org.mpisws.strategies.RandomSchedulingStrategy;
import org.mpisws.strategies.SchedulingStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcCheckConfiguration {
    String strategy() default "random";

    Class<? extends SchedulingStrategy> customStrategy() default RandomSchedulingStrategy.class;

    int numIterations() default 10;

    boolean debug() default false;

    String bugsPath() default "build/test-results/jmc-bugs";

    long seed() default 0;
}
