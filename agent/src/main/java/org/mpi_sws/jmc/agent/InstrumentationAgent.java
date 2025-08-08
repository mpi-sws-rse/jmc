package org.mpi_sws.jmc.agent;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

/**
 * The InstrumentationAgent class is the entry point for the instrumentation agent. It is used to
 * set up the agent and install the instrumentation on the target application.
 */
public class InstrumentationAgent {

    private static final Logger LOGGER = LogManager.getLogger(InstrumentationAgent.class);

    private static void loadDependencyJars(Instrumentation inst, String jmcRuntimeJarPath) {
        try {
            InputStream in = Files.newInputStream(new File(jmcRuntimeJarPath).toPath());
            File tempFile = File.createTempFile("jmc-runtime", ".jar");
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            inst.appendToSystemClassLoaderSearch(new JarFile(tempFile));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load JMC runtime jar", e);
        }
    }

    /**
     * The premain method is called before the application's main method is called. It is used to
     * set up the instrumentation agent.
     *
     * @param agentArgs the agent arguments
     * @param inst the instrumentation object
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        AgentArgs args = new AgentArgs(agentArgs);
        LOGGER.info("Starting JMC agent");
        System.out.println("Starting JMC agent");
        //        if (args.isDebug()) {
        //            Configurator.setRootLevel(Level.DEBUG);
        //        }
        System.out.println("Agent arguments: " + agentArgs);
        LOGGER.debug("Arguments: {}", agentArgs);
        loadDependencyJars(inst, args.getJmcRuntimeJarPath());

        PremainInstrumentor instrumentor = new PremainInstrumentor(args);
        inst.addTransformer(instrumentor, true);
    }
}
