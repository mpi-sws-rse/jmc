package org.mpisws.jmc.integrations.junit5.descriptors;

import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcTimeout;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.integrations.junit5.engine.JmcTestExecutor;

import java.lang.reflect.Method;
import java.time.Duration;

public class JmcMethodTestDescriptor extends AbstractTestDescriptor
        implements JmcExecutableTestDescriptor {

    private final Method testMethod;

    public JmcMethodTestDescriptor(
            Method testMethod, JmcClassTestDescriptor parent) {
        super(
                parent.getUniqueId().append("method", testMethod.getName()),
                testMethod.getName(),
                ClassSource.from(testMethod.getDeclaringClass()));
        this.testMethod = testMethod;
        setParent(parent);

    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    public void execute() throws Exception {
        System.out.println("JmcMethodTestDescriptor execute() called");
        Object instance = testMethod.getDeclaringClass().getDeclaredConstructor().newInstance();
        testMethod.setAccessible(true);

        JmcCheckConfiguration annotation = testMethod.getAnnotation(JmcCheckConfiguration.class);
//        Replay replayAnnotation = testMethod.getAnnotation(Replay.class);
        JmcTimeout annotationTimeout = testMethod.getAnnotation(JmcTimeout.class);
        JmcCheckerConfiguration config;


        if (annotation == null) {
            annotation = testMethod.getDeclaringClass().getAnnotation(JmcCheckConfiguration.class);
        }

        if (annotation == null) {
            System.out.println("No configuration found — invoking method directly.");
            testMethod.invoke(instance);
            return;
        }

        if (annotationTimeout != null) {
            System.out.println("Under if numIterations=" + annotation.numIterations());
            config =
                    new JmcCheckerConfiguration.Builder()
                            .numIterations(annotation.numIterations())
                            .debug(annotation.debug())
                            .seed(annotation.seed())
                            .reportPath(annotation.reportPath())
                            .strategyType(annotation.strategy())
                            .timeout(Duration.ofSeconds(annotationTimeout.value()))
                            .build();
            System.out.println("config is null? " + config);
        } else {
            System.out.println("Under else numIterations=" + annotation.numIterations());
            config =
                    new JmcCheckerConfiguration.Builder()
                            .numIterations(annotation.numIterations())
                            .debug(annotation.debug())
                            .seed(annotation.seed())
                            .reportPath(annotation.reportPath())
                            .strategyType(annotation.strategy())
                            .build();
            System.out.println("config is null? " + config);
        }
//        if (replayAnnotation != null) {
//            System.out.println("is in replay if condition " + config);
//            config =
//                    new JmcCheckerConfiguration.Builder()
//                            .numIterations(replayAnnotation.numIterations())
//                            .debug(annotation.debug())
//                            .seed(annotation.seed())
//                            .reportPath(annotation.reportPath())
//                            .strategyType(annotation.strategy())
//                            .build();
//
//            JmcTestExecutor.executeReplay(testMethod, instance, config, annotation.seed(), annotation.numIterations());
//
//        }
        System.out.println("outside checks config is null? " + config);
        JmcTestExecutor.execute(testMethod, instance, config);
    }
}
