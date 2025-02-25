package org.mpisws.instrumentation.agent;

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
            Class<?> aClass,
            ProtectionDomain protectionDomain) {
        return classLoader != null;
    }
}
