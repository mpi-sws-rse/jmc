package org.mpi_sws.jmc.integrations.junit5.descriptors;

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;

/**
 * A JUnit 5 engine descriptor for the JMC engine.
 *
 * <p>This descriptor represents the JMC engine in the JUnit 5 test framework, allowing for the
 * execution of JMC checks as part of the test lifecycle. It is the <em>root</em> of the descriptor
 * tree — {@code JmcTestEngine.discover} creates one per discovery run and attaches the discovered
 * {@link JmcClassTestDescriptor}s as its children.
 */
public class JmcEngineDescriptor extends EngineDescriptor {

    /** Display name shown for the JMC engine in JUnit tooling/reports. */
    public static final String ENGINE_DISPLAY_NAME = "JMC (JUnit platform)";

    /**
     * Creates the root engine descriptor.
     *
     * @param uniqueId the engine's unique id on the JUnit Platform (from {@code discover})
     */
    public JmcEngineDescriptor(UniqueId uniqueId) {
        super(uniqueId, ENGINE_DISPLAY_NAME);
    }
}
