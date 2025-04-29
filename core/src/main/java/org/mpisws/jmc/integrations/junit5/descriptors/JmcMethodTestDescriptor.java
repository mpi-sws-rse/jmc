package org.mpisws.jmc.integrations.junit5.descriptors;

import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.annotations.JmcTimeout;
import org.mpisws.jmc.annotations.Replay;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.integrations.junit5.engine.JmcTestExecutor;

import java.lang.reflect.Method;
import java.time.Duration;

public class JmcMethodTestDescriptor extends AbstractTestDescriptor
        implements JmcExecutableTestDescriptor {

    private final Method testMethod;
    private final JmcCheckerConfiguration classConfig;

    public JmcMethodTestDescriptor(
            Method testMethod, JmcClassTestDescriptor parent, JmcCheckerConfiguration classConfig) {
        super(
                parent.getUniqueId().append("method", testMethod.getName()),
                testMethod.getName(),
                ClassSource.from(testMethod.getDeclaringClass()));
        this.testMethod = testMethod;
        this.classConfig = classConfig;
        setParent(parent);
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    public void execute() throws Exception {
        Object instance = testMethod.getDeclaringClass().getDeclaredConstructor().newInstance();
        testMethod.setAccessible(true);

        JmcCheckConfiguration annotation = testMethod.getAnnotation(JmcCheckConfiguration.class);
        Replay replayAnnotation = testMethod.getAnnotation(Replay.class);
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
        }

        if (replayAnnotation != null) {
            config =
                    new JmcCheckerConfiguration.Builder()
                            .numIterations(replayAnnotation.numIterations())
                            .debug(annotation.debug())
                            .seed(annotation.seed())
                            .reportPath(annotation.reportPath())
                            .strategyType(annotation.strategy())
                            .build();

            JmcTestExecutor.executeReplay(testMethod, instance, config, annotation.seed(), annotation.numIterations());

        }

        JmcTestExecutor.execute(testMethod, instance, config);
    }
}
