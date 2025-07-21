package org.mpisws.jmc.integrations.junit5.descriptors;

import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;

import static org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode.TOP_DOWN;

/** A JUnit 5 test descriptor for a JMC class test. */
public class JmcClassTestDescriptor extends AbstractTestDescriptor {
    private JmcCheckConfiguration config;
    private final Class<?> testClass;

    public JmcClassTestDescriptor(Class<?> testClass, TestDescriptor parent, boolean selfDiscovery)
            throws JmcCheckerException {
        super(
                parent.getUniqueId().append("class", testClass.getName()),
                testClass.getSimpleName(),
                ClassSource.from(testClass));
        this.testClass = testClass;
        setParent(parent);

        // Resolving class level configuration
        JmcCheckConfiguration annotation = testClass.getAnnotation(JmcCheckConfiguration.class);
        JmcCheck jmcCheckAnnotation = testClass.getAnnotation(JmcCheck.class);
        if (annotation != null || jmcCheckAnnotation != null) {
            this.config = annotation;
        }
        if (selfDiscovery) {
            discoverChildren();
        }
    }

    public JmcCheckConfiguration getConfigAnnotation() {
        return config;
    }

    private void discoverChildren() {
        JmcCheckConfiguration classAnnotation =
                testClass.getAnnotation(JmcCheckConfiguration.class);
        boolean classHasAnnotation = classAnnotation != null;

        ReflectionUtils.findMethods(
                        testClass,
                        method ->
                                method.isAnnotationPresent(JmcCheckConfiguration.class)
                                        || method.isAnnotationPresent(JmcCheck.class)
                                        || classHasAnnotation,
                        TOP_DOWN)
                .forEach(
                        method -> {
                            addChild(new JmcMethodTestDescriptor(method, this));
                        });
    }

    @Override
    public Type getType() {
        return Type.CONTAINER_AND_TEST;
    }
}
