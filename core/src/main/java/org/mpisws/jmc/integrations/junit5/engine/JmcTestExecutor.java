package org.mpisws.jmc.integrations.junit5.engine;

import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.mpisws.jmc.integrations.junit5.descriptors.JmcMethodTestDescriptor;

public class JmcTestExecutor {

    public void execute(TestDescriptor descriptor, EngineExecutionListener listener) {
        listener.executionStarted(descriptor);

        try {
            if (descriptor.isContainer()) {
                for (TestDescriptor child : descriptor.getChildren()) {
                    execute(child, listener);
                }
            } else if (descriptor instanceof JmcMethodTestDescriptor methodTestDescriptor) {
                methodTestDescriptor.execute();
            }
            listener.executionFinished(descriptor, TestExecutionResult.successful());
        } catch (Throwable t) {
            listener.executionFinished(
                    descriptor,
                    TestExecutionResult.failed(t)
            );
        }
    }

}


