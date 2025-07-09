package org.mpisws.jmc.integrations.junit5.descriptors;

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;

/**
 * A JUnit 5 engine descriptor for the JMC engine.
 *
 * <p>This descriptor represents the JMC engine in the JUnit 5 test framework, allowing for the
 * execution of JMC checks as part of the test lifecycle.
 */
public class JmcEngineDescriptor extends EngineDescriptor {

    public static final String ENGINE_DISPLAY_NAME = "JMC (JUnit platform)";

    public JmcEngineDescriptor(UniqueId uniqueId) {
        super(uniqueId, ENGINE_DISPLAY_NAME);
    }
}
