package org.mpisws.jmc.integrations.junit5.descriptors;

import org.junit.platform.commons.util.AnnotationUtils;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

import static org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode.TOP_DOWN;

public class JmcClassTestDescriptor extends AbstractTestDescriptor {
    private JmcCheckerConfiguration config;
    private Class<?> testClass;

    public JmcClassTestDescriptor(UniqueId uniqueId, Class<?> testClass) {
        super(uniqueId, JmcEngineDescriptor.ENGINE_DISPLAY_NAME);
        this.testClass = testClass;
        JmcCheckConfiguration annotation = testClass.getAnnotation(JmcCheckConfiguration.class);
        if (annotation != null) {
            this.config = JmcCheckerConfiguration.fromAnnotation(annotation);
        } else {
            this.config = new JmcCheckerConfiguration.Builder().build();
        }
    }

    public JmcClassTestDescriptor(Class<?> testClass, TestDescriptor parent) {
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

//    private void addAllChildren() {
//        Predicate<Method> isTestMethod = method -> AnnotationUtils.isAnnotated(method, JmcCheckConfiguration.class);
//
//        ReflectionUtils
//                .findMethods(testClass, isTestMethod, TOP_DOWN)
//                .stream()
//                .map(method -> new JmcMethodTestDescriptor(method, this))
//                .forEach(this::addChild);
//
//    }

    private void discoverChildren() {
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
                        effectiveConfig = JmcCheckerConfiguration.fromAnnotation(methodAnnotation);
                    } else {
                        effectiveConfig = JmcCheckerConfiguration.fromAnnotation(classAnnotation);
                    }

                    addChild(new JmcMethodTestDescriptor(method, this, effectiveConfig));
                    });

    }

    @Override
    public Type getType() {
        return Type.CONTAINER;
    }
}
