package org.mpisws.jmc.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;

import java.security.ProtectionDomain;
import java.util.List;
import java.util.Objects;

/**
 * Matcher for JMC agent.
 *
 * <p>Currently filters out classes loaded by built-in class loader.
 */
public class JmcMatcher implements AgentBuilder.RawMatcher {

    private List<String> matchingPackages;

    public JmcMatcher(List<String> matchingPackages) {
        this.matchingPackages = matchingPackages;
    }

    @Override
    public boolean matches(
            TypeDescription typeDescription,
            ClassLoader classLoader,
            JavaModule javaModule,
            Class<?> aclass,
            ProtectionDomain protectionDomain) {
        if (classLoader == null) {
            return false;
        }
        String classLoaderName = classLoader.getClass().getName();
        if (classLoaderName.contains("Gradle")
                || classLoaderName.contains("Maven")
                || classLoaderName.contains("Ant")
                || classLoaderName.contains("sbt")) {
            return false;
        }
        String typeName = typeDescription.getName();
        if (typeName.startsWith("java.")
                || typeName.startsWith("javax.")
                || typeName.startsWith("sun.")
                || typeName.startsWith("com.sun.")
                || typeName.startsWith("jdk.")
                || typeName.startsWith("kotlin.")
                || typeName.startsWith("kotlinx.")
                || typeName.startsWith("org.gradle.")
                || typeName.startsWith("org.slf4j.")
                || typeName.startsWith("worker.org.gradle.")
                || typeName.startsWith("org.junit.")) {
            return false;
        }
        if (!matchingPackages.isEmpty()) {
            boolean out = matchingPackages.stream().anyMatch(typeName::startsWith);
            if (out) {
                System.out.println("Matched: " + typeName);
            }
            return out;
        } else {
            System.out.println("Matched: " + typeName);
            return true;
        }
    }
}
