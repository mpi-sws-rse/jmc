package org.mpisws.integrations.junit5.descriptors;

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.mpisws.annotations.JmcCheckConfiguration;
import org.mpisws.checker.JmcCheckerConfiguration;

public class JmcTestClassDescriptor extends AbstractTestDescriptor {
    private JmcCheckerConfiguration config;
    private Class<?> testClass;

    public JmcTestClassDescriptor(UniqueId uniqueId, Class<?> testClass) {
        super(uniqueId, JmcTestEngineDescriptor.ENGINE_DISPLAY_NAME);
        this.testClass = testClass;
        JmcCheckConfiguration annotation = testClass.getAnnotation(JmcCheckConfiguration.class);
        if (annotation != null) {
            this.config = JmcCheckerConfiguration.fromAnnotation(annotation);
        } else {
            this.config = new JmcCheckerConfiguration.Builder().build();
        }
    }

    @Override
    public Type getType() {
        return Type.CONTAINER_AND_TEST;
    }
}
