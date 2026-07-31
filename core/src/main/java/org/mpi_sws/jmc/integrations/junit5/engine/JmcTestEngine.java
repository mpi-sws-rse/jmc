package org.mpi_sws.jmc.integrations.junit5.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.*;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.discovery.PackageSelector;
import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.integrations.junit5.descriptors.JmcClassTestDescriptor;
import org.mpi_sws.jmc.integrations.junit5.descriptors.JmcEngineDescriptor;
import org.mpi_sws.jmc.integrations.junit5.descriptors.JmcExecutableTestDescriptor;
import org.mpi_sws.jmc.integrations.junit5.descriptors.JmcMethodTestDescriptor;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.function.Predicate;

/**
 * A custom JUnit 5 test engine for running JMC tests.
 *
 * <p>This engine discovers and executes tests annotated with {@link JmcCheck} or {@link
 * JmcCheckConfiguration} in the classpath, packages, or specific classes.
 */
public class JmcTestEngine implements TestEngine {

    /** Logger for discovery/execution diagnostics. */
    private static final Logger LOGGER = LogManager.getLogger(JmcTestEngine.class);

    /**
     * Predicate identifying a "JMC test container": a class annotated with {@link
     * JmcCheckConfiguration} or {@link JmcCheck}. Used throughout discovery to decide whether a class
     * (or a method's declaring class) is a JMC test.
     */
    private static final Predicate<Class<?>> IS_JMC_TEST_CONTAINER =
            classCandidate ->
                    AnnotationSupport.isAnnotated(classCandidate, JmcCheckConfiguration.class)
                            || AnnotationSupport.isAnnotated(classCandidate, JmcCheck.class);

    /**
     * Returns the unique id of this engine on the JUnit Platform.
     *
     * @return the constant engine id {@code "jmc-test-engine"}
     */
    @Override
    public String getId() {
        return "jmc-test-engine";
    }

    /**
     * Discovers tests based on the provided discovery request and unique ID.
     *
     * <p>This method scans the classpath, packages, and specific classes for JMC tests annotated
     * with {@link JmcCheck} or {@link JmcCheckConfiguration}. It creates a test descriptor for the
     * JMC test engine and adds discovered tests as children of the engine descriptor.
     *
     * @param request The discovery request containing selectors for classpath roots, packages, and
     *     classes.
     * @param uniqueId The unique ID for the test engine descriptor.
     * @return A {@link TestDescriptor} representing the discovered tests in the JMC test engine.
     */
    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
        LOGGER.debug("Discovering tests");
        JmcEngineDescriptor engineDescriptor = new JmcEngineDescriptor(uniqueId);

        request.getSelectorsByType(ClasspathRootSelector.class)
                .forEach(
                        selector -> {
                            appendTestsInClasspathRoot(
                                    selector.getClasspathRoot(), engineDescriptor);
                        });

        request.getSelectorsByType(PackageSelector.class)
                .forEach(
                        selector -> {
                            appendTestsInPackage(selector.getPackageName(), engineDescriptor);
                        });

        request.getSelectorsByType(ClassSelector.class)
                .forEach(
                        selector -> {
                            try {
                                appendTestsInClass(selector.getJavaClass(), engineDescriptor);
                            } catch (JmcCheckerException e) {
                                throw new RuntimeException(e);
                            }
                        });
        request.getSelectorsByType(MethodSelector.class)
                .forEach(
                        (selector) -> {
                            try {
                                Class<?> javaClass = selector.getJavaClass();
                                Method method = selector.getJavaMethod();
                                if (IS_JMC_TEST_CONTAINER.test(javaClass)) {
                                    engineDescriptor.addChild(
                                            new JmcClassTestDescriptor(
                                                    javaClass, engineDescriptor, false));
                                } else {
                                    appendTestsInClass(javaClass, engineDescriptor);
                                }
                            } catch (JmcCheckerException e) {
                                throw new RuntimeException(e);
                            }
                        });

        return engineDescriptor;
    }

    /**
     * Discovers JMC test classes under a classpath root and adds them as children of the engine
     * descriptor.
     *
     * <p>Scans {@code uri} with {@link ReflectionSupport#findAllClassesInClasspathRoot} filtered by
     * {@link #IS_JMC_TEST_CONTAINER}, mapping each matching class to a self-discovering {@link
     * JmcClassTestDescriptor} (so its annotated methods become children). A checked {@link
     * JmcCheckerException} raised while building a descriptor is wrapped in a {@link RuntimeException}.
     *
     * @param uri the classpath root to scan
     * @param engineDescriptor the root descriptor to attach discovered classes to
     */
    private void appendTestsInClasspathRoot(URI uri, TestDescriptor engineDescriptor) {
        ReflectionSupport.findAllClassesInClasspathRoot(
                        uri, IS_JMC_TEST_CONTAINER, name -> true) //
                .stream() //
                .map(
                        aClass -> {
                            try {
                                return new JmcClassTestDescriptor(aClass, engineDescriptor, true);
                            } catch (JmcCheckerException e) {
                                throw new RuntimeException(e);
                            }
                        }) //
                .forEach(engineDescriptor::addChild);
    }

    /**
     * Discovers JMC test classes in a package and adds them as children of the engine descriptor.
     *
     * <p>Same behavior as {@link #appendTestsInClasspathRoot} but scoped to a package via {@link
     * ReflectionSupport#findAllClassesInPackage}: each JMC-container class becomes a self-discovering
     * {@link JmcClassTestDescriptor}.
     *
     * @param packageName the package to scan
     * @param engineDescriptor the root descriptor to attach discovered classes to
     */
    private void appendTestsInPackage(String packageName, TestDescriptor engineDescriptor) {
        LOGGER.debug("Discovering tests in package {}", packageName);
        ReflectionSupport.findAllClassesInPackage(
                        packageName, IS_JMC_TEST_CONTAINER, name -> true) //
                .stream() //
                .map(
                        aClass -> {
                            try {
                                return new JmcClassTestDescriptor(aClass, engineDescriptor, true);
                            } catch (JmcCheckerException e) {
                                throw new RuntimeException(e);
                            }
                        }) //
                .forEach(engineDescriptor::addChild);
    }

    /**
     * Discovers the JMC tests contributed by a single class and attaches them to the engine
     * descriptor.
     *
     * <p>If {@code javaClass} is itself a JMC container, it is added as a self-discovering {@link
     * JmcClassTestDescriptor} (its annotated methods become children). Otherwise the class is searched
     * (top-down) for methods annotated with {@link JmcCheckConfiguration} or {@link JmcCheck}: if none
     * exist nothing is added; if some exist, a non-self-discovering {@link JmcClassTestDescriptor} is
     * added and a {@link JmcMethodTestDescriptor} child is created for each annotated method.
     *
     * @param javaClass the class to inspect
     * @param engineDescriptor the root descriptor to attach discovered tests to
     * @throws JmcCheckerException if building a {@link JmcClassTestDescriptor} fails
     */
    private void appendTestsInClass(Class<?> javaClass, TestDescriptor engineDescriptor)
            throws JmcCheckerException {
        LOGGER.debug("Discovering tests in class {}", javaClass.getName());
        if (IS_JMC_TEST_CONTAINER.test(javaClass)) {
            engineDescriptor.addChild(
                    new JmcClassTestDescriptor(javaClass, engineDescriptor, true));
        } else {
            List<Method> methods =
                    ReflectionSupport.findMethods(
                            javaClass,
                            (method) ->
                                    method.getAnnotation(JmcCheckConfiguration.class) != null
                                            || method.getAnnotation(JmcCheck.class) != null,
                            HierarchyTraversalMode.TOP_DOWN);

            if (methods.isEmpty()) {
                return;
            }
            JmcClassTestDescriptor testDescriptor =
                    new JmcClassTestDescriptor(javaClass, engineDescriptor, false);
            engineDescriptor.addChild(testDescriptor);

            methods.forEach(
                    (method) -> {
                        if (method.getAnnotation(JmcCheckConfiguration.class) != null
                                || method.getAnnotation(JmcCheck.class) != null) {
                            testDescriptor.addChild(
                                    new JmcMethodTestDescriptor(method, testDescriptor));
                        }
                    });
        }
    }

    /**
     * Executes the discovered tests in the JMC test engine.
     *
     * <p>This method starts the execution of the root test descriptor and recursively executes all
     * child descriptors, handling any exceptions that may occur during execution.
     *
     * @param request The execution request containing the root test descriptor and engine execution
     *     listener.
     */
    @Override
    public void execute(ExecutionRequest request) {
        TestDescriptor root = request.getRootTestDescriptor();
        request.getEngineExecutionListener().executionStarted(root);

        for (TestDescriptor child : root.getChildren()) {
            executeDescriptor(request.getEngineExecutionListener(), child);
        }

        request.getEngineExecutionListener()
                .executionFinished(root, TestExecutionResult.successful());
    }

    /**
     * Recursively executes a descriptor subtree, reporting results to the JUnit listener.
     *
     * <p>If the descriptor is a runnable leaf (a {@link JmcExecutableTestDescriptor}, i.e. a {@link
     * JmcMethodTestDescriptor}), it reports {@code executionStarted}, calls {@link
     * JmcExecutableTestDescriptor#execute()}, and reports {@code executionFinished} with a successful
     * result — or a failed result carrying the thrown {@link Throwable}. Otherwise (a container) it
     * recurses into the descriptor's children; containers are never themselves "executed".
     *
     * @param listener the JUnit execution listener to report start/finish events to
     * @param descriptor the descriptor subtree to execute
     */
    private void executeDescriptor(EngineExecutionListener listener, TestDescriptor descriptor) {
        if (descriptor instanceof JmcExecutableTestDescriptor exec) {
            listener.executionStarted(descriptor);
            try {
                exec.execute();
                listener.executionFinished(descriptor, TestExecutionResult.successful());
            } catch (Throwable t) {
                listener.executionFinished(descriptor, TestExecutionResult.failed(t));
            }
        } else {
            for (TestDescriptor child : descriptor.getChildren()) {
                executeDescriptor(listener, child);
            }
        }
    }
}
