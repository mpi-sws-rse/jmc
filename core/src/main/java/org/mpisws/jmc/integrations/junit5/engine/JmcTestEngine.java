package org.mpisws.jmc.integrations.junit5.engine;

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
import org.mpisws.jmc.annotations.JmcCheck;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.integrations.junit5.descriptors.JmcClassTestDescriptor;
import org.mpisws.jmc.integrations.junit5.descriptors.JmcEngineDescriptor;
import org.mpisws.jmc.integrations.junit5.descriptors.JmcExecutableTestDescriptor;
import org.mpisws.jmc.integrations.junit5.descriptors.JmcMethodTestDescriptor;

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

    private static final Logger LOGGER = LogManager.getLogger(JmcTestEngine.class);

    private static final Predicate<Class<?>> IS_JMC_TEST_CONTAINER =
            classCandidate ->
                    AnnotationSupport.isAnnotated(classCandidate, JmcCheckConfiguration.class)
                            || AnnotationSupport.isAnnotated(classCandidate, JmcCheck.class);

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
