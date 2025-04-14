package org.mpisws.jmc.integrations.junit5.engine;

import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.*;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.PackageSelector;
import org.mpisws.jmc.integrations.junit5.descriptors.JmcClassTestDescriptor;
import org.mpisws.jmc.integrations.junit5.descriptors.JmcEngineDescriptor;

import java.net.URI;
import java.util.function.Predicate;

public class JmcTestEngine implements TestEngine {

    private static final Predicate<Class<?>> IS_JMC_TEST_CONTAINER
            = classCandidate -> AnnotationSupport.isAnnotated(classCandidate, JmcTest.class);

    @Override
    public String getId() {
        return "jmc-test-engine";
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
        TestDescriptor engineDescriptor = new JmcEngineDescriptor(uniqueId);

        request.getSelectorsByType(ClasspathRootSelector.class).forEach(selector -> {
            appendTestsInClasspathRoot(selector.getClasspathRoot(), engineDescriptor);
        });

        request.getSelectorsByType(PackageSelector.class).forEach(selector -> {
            appendTestsInPackage(selector.getPackageName(), engineDescriptor);
        });

        request.getSelectorsByType(ClassSelector.class).forEach(selector -> {
            appendTestsInClass(selector.getJavaClass(), engineDescriptor);
        });

        return engineDescriptor;
    }

    private void appendTestsInClasspathRoot(URI uri, TestDescriptor engineDescriptor) {
        ReflectionSupport.findAllClassesInClasspathRoot(uri, IS_JMC_TEST_CONTAINER, name -> true) //
                .stream()//
                .map(aClass -> new JmcClassTestDescriptor(aClass, engineDescriptor)) //
                .forEach(engineDescriptor::addChild);
    }

    private void appendTestsInPackage(String packageName, TestDescriptor engineDescriptor) {
        ReflectionSupport.findAllClassesInPackage(packageName, IS_JMC_TEST_CONTAINER, name -> true) //
        .stream() //
        .map(aClass -> new JmcClassTestDescriptor(aClass, engineDescriptor)) //
                .forEach(engineDescriptor::addChild);
    }

    private void appendTestsInClass(Class<?> javaClass, TestDescriptor engineDescriptor) {
        if (AnnotationSupport.isAnnotated(javaClass, JmcTest.class)) {
            engineDescriptor.addChild(new JmcClassTestDescriptor(javaClass, engineDescriptor));
        }
    }



    @Override
    public void execute(ExecutionRequest request) {
        TestDescriptor root = request.getRootTestDescriptor();
        EngineExecutionListener listener = request.getEngineExecutionListener();

        new JmcTestExecutor().execute(root, listener);
    }
}
