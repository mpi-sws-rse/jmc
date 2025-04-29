package org.mpisws.jmc.integrations.junit5.descriptors;

import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;


import static org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode.TOP_DOWN;

public class JmcClassTestDescriptor extends AbstractTestDescriptor {
    private JmcCheckerConfiguration config;
    private Class<?> testClass;


    public JmcClassTestDescriptor(Class<?> testClass, TestDescriptor parent) throws JmcCheckerException {
        super(
                parent.getUniqueId().append("class", testClass.getName()),
                testClass.getSimpleName(),
                ClassSource.from(testClass)
        );
        this.testClass = testClass;
        setParent(parent);

        //Resolving class level configuration
        JmcCheckConfiguration annotation = testClass.getAnnotation(JmcCheckConfiguration.class);
        if (annotation != null) {
            this.config = JmcCheckerConfiguration.fromAnnotation(annotation);
        }

        discoverChildren();
    }


    private void discoverChildren()  {
        JmcCheckConfiguration classAnnotation = testClass.getAnnotation(JmcCheckConfiguration.class);
        boolean classHasAnnotation = classAnnotation != null;

        ReflectionUtils
                .findMethods(testClass,
                        method ->  method.isAnnotationPresent(JmcCheckConfiguration.class) || classHasAnnotation,
                        TOP_DOWN)
                .forEach(method -> {
                    JmcCheckConfiguration methodAnnotation = method.getAnnotation(JmcCheckConfiguration.class);
                    JmcCheckerConfiguration effectiveConfig;
                    if (methodAnnotation != null) {
                        try {
                            effectiveConfig = JmcCheckerConfiguration.fromAnnotation(methodAnnotation);
                        } catch (JmcCheckerException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        try {
                            effectiveConfig = JmcCheckerConfiguration.fromAnnotation(classAnnotation);
                        } catch (JmcCheckerException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    addChild(new JmcMethodTestDescriptor(method, this, effectiveConfig));
                    });

    }

    @Override
    public Type getType() {
        return Type.CONTAINER;
    }
}
