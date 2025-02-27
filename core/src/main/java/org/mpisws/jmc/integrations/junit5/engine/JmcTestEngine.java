package org.mpisws.jmc.integrations.junit5.engine;

import org.junit.platform.engine.*;

public class JmcTestEngine implements TestEngine {
    @Override
    public String getId() {
        return "jmc-test-engine";
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
        return null;
    }

    @Override
    public void execute(ExecutionRequest request) {}
}
