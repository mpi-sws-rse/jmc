package org.mpisws.jmc.agent;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * The InstrumentationAgent class is the entry point for the instrumentation agent. It is used to
 * set up the agent and install the instrumentation on the target application.
 */
public class InstrumentationAgent {

    private static void loadDependencyJars(Instrumentation inst) {
        String jmcRuntimeJarPath = "/lib/jmc-0.1.0.jar";
        try {
            InputStream in = InstrumentationAgent.class.getResourceAsStream(jmcRuntimeJarPath);
            if (in == null) {
                throw new RuntimeException("Could not find JMC runtime jar");
            }
            File tempFile = File.createTempFile("jmc-runtime", ".jar");
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            inst.appendToSystemClassLoaderSearch(new JarFile(tempFile));
        } catch (Exception e) {
            System.err.println("Could not find JMC runtime jar");
        }
    }

    /**
     * The agentmain method is called when the agent is attached to the target application. It is
     * used to set up the instrumentation agent.
     *
     * @param agentArgs the agent arguments
     * @param inst the instrumentation object
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        AgentArgs args = new AgentArgs(agentArgs);
        AgentMainInstrumentor agentMainInstrumentor = new AgentMainInstrumentor(args);
        inst.addTransformer(agentMainInstrumentor);
    }

    /**
     * The premain method is called before the application's main method is called. It is used to
     * set up the instrumentation agent.
     *
     * @param agentArgs the agent arguments
     * @param inst the instrumentation object
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        loadDependencyJars(inst);
        AgentArgs args = new AgentArgs(agentArgs);

        PremainInstrumentor instrumentor = new PremainInstrumentor(args);
        inst.addTransformer(instrumentor, true);
    }
}
