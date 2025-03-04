package org.mpisws.jmc.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;

import java.security.ProtectionDomain;

/**
 * Matcher for JMC agent.
 *
 * <p>Currently filters out classes loaded by built-in class loader.
 */
public class JmcMatcher implements AgentBuilder.RawMatcher {
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
        return !typeName.startsWith("java.")
                && !typeName.startsWith("javax.")
                && !typeName.startsWith("sun.")
                && !typeName.startsWith("com.sun.")
                && !typeName.startsWith("jdk.")
                && !typeName.startsWith("kotlin.")
                && !typeName.startsWith("kotlinx.")
                && !typeName.startsWith("org.gradle.")
                && !typeName.startsWith("org.slf4j.")
                && !typeName.startsWith("worker.org.gradle.")
                && !typeName.startsWith("org.junit.");
    }
}
