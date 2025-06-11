package org.mpisws.jmc.agent;

import java.lang.instrument.ClassFileTransformer;
import java.util.Arrays;

public class AgentMainInstrumentor implements ClassFileTransformer {
    private AgentArgs agentArgs;
    private JmcMatcher matcher;

    public AgentMainInstrumentor(AgentArgs agentArgs) {
        this.agentArgs = agentArgs;
        this.matcher = new JmcMatcher(agentArgs.getInstrumentingPackages(), agentArgs.getExcludedPackages());
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            java.security.ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        String finalClassName = className.replace("/", ".");
        byte[] copiedClassBuffer = Arrays.copyOf(classfileBuffer, classfileBuffer.length);
        if (!this.matcher.matches(finalClassName, loader)) {
            return copiedClassBuffer;
        }
        // Transformation logic goes here
        return copiedClassBuffer;
    }
}
