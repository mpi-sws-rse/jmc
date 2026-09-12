package org.mpi_sws.jmc.integrations.junit5.descriptors;

import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;

import static org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode.TOP_DOWN;

/**
 * A JUnit 5 test descriptor for a JMC test class (a container node in the descriptor tree).
 *
 * <p>The engine creates one of these per discovered JMC test class, as a child of the {@link
 * JmcEngineDescriptor}; its own children are the {@link JmcMethodTestDescriptor}s for the class's
 * test methods. It captures the class-level {@link JmcCheckConfiguration} (exposed via {@link
 * #getConfigAnnotation()}) so each child method can fall back to it, and — when constructed with
 * self-discovery — enumerates those method children itself. Its {@link #getType() type} is {@link
 * Type#CONTAINER_AND_TEST}.
 */
public class JmcClassTestDescriptor extends AbstractTestDescriptor {
    /**
     * The class-level {@link JmcCheckConfiguration}, or {@code null} if the class has none (e.g. it is
     * marked only with {@link JmcCheck}). Read by child method descriptors as a fallback config.
     */
    private JmcCheckConfiguration config;
    /** The test class this descriptor represents. */
    private final Class<?> testClass;

    /**
     * Builds a class descriptor for {@code testClass} under {@code parent}.
     *
     * <p>Sets the unique id (parent's id + {@code ("class", className)}), the simple class name as the
     * display name, and a {@link ClassSource}. It resolves the class-level configuration: if the class
     * carries {@link JmcCheckConfiguration} or {@link JmcCheck}, {@link #config} is set to the {@link
     * JmcCheckConfiguration} annotation (which is {@code null} when only {@link JmcCheck} is present).
     * When {@code selfDiscovery} is {@code true}, it immediately discovers its method children via
     * {@link #discoverChildren()}.
     *
     * @param testClass the JMC test class to describe
     * @param parent the parent descriptor (typically the {@link JmcEngineDescriptor})
     * @param selfDiscovery whether to enumerate this class's test methods in the constructor
     * @throws JmcCheckerException if the descriptor cannot be built
     */
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

    /**
     * Returns the class-level {@link JmcCheckConfiguration}, used by {@link JmcMethodTestDescriptor} as
     * a fallback when a method has no configuration of its own.
     *
     * @return the class-level configuration annotation, or {@code null} if there is none
     */
    public JmcCheckConfiguration getConfigAnnotation() {
        return config;
    }

    /**
     * Enumerates this class's test methods and adds a {@link JmcMethodTestDescriptor} child for each.
     *
     * <p>Using a top-down search, it selects methods annotated with {@link JmcCheckConfiguration} or
     * {@link JmcCheck} — or, if the class itself carries {@link JmcCheckConfiguration}, <em>every</em>
     * method. Called from the constructor when self-discovery is requested.
     */
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

    /**
     * @return {@link Type#CONTAINER_AND_TEST} — this node both contains method tests and is itself
     *     addressable as a test
     */
    @Override
    public Type getType() {
        return Type.CONTAINER_AND_TEST;
    }
}
