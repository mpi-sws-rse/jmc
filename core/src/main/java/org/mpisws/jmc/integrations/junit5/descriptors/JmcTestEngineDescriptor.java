package org.mpisws.jmc.integrations.junit5.descriptors;

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;

public class JmcTestEngineDescriptor extends EngineDescriptor {

    public static final String ENGINE_DISPLAY_NAME = "JMC (JUnit platform)";

    public JmcTestEngineDescriptor(UniqueId uniqueId) {
        super(uniqueId, ENGINE_DISPLAY_NAME);
    }
}
