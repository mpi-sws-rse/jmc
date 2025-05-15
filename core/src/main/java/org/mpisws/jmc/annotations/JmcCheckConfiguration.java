package org.mpisws.jmc.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JmcCheckConfiguration {
    String strategy() default "random";

    int numIterations() default 0;

    boolean debug() default false;

    String reportPath() default "build/test-results/jmc-report";

    long seed() default 0;
}
