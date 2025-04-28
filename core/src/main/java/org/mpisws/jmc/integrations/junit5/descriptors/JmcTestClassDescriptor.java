package org.mpisws.jmc.integrations.junit5.descriptors;

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;

public class JmcTestClassDescriptor extends AbstractTestDescriptor {
    private JmcCheckerConfiguration config;
    private Class<?> testClass;

    public JmcTestClassDescriptor(UniqueId uniqueId, Class<?> testClass)
            throws JmcCheckerException {
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
