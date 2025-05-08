package org.mpisws.jmc.integrations.junit5.descriptors;

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;

import java.lang.reflect.Method;

public class JmcTestMethodDescriptor extends AbstractTestDescriptor {

    private Method testMethod;
    private Class<?> testClass;

    protected JmcTestMethodDescriptor(UniqueId uniqueId, Class<?> testClass, Method testMethod) {
        super(uniqueId, JmcTestEngineDescriptor.ENGINE_DISPLAY_NAME);
        this.testClass = testClass;
        this.testMethod = testMethod;
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }
}
