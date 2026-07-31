package org.mpi_sws.jmc.agent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

/**
 * Entry point of the JMC Java agent.
 *
 * <p>This class is named as the agent's {@code Premain-Class}, so its {@link #premain} method is
 * invoked by the JVM (via the {@code -javaagent} flag) before the target application's {@code main}
 * runs. Its job is to bootstrap JMC's bytecode instrumentation:
 *
 * <ol>
 *   <li>load the JMC runtime jar so that the JMC types referenced by the instrumentation (for
 *       example {@code JmcThread}, {@code JmcReentrantLock}, {@code JmcRuntimeUtils}) are resolvable
 *       while later classes are being transformed, and
 *   <li>register a {@link PremainInstrumentor} as a {@link java.lang.instrument.ClassFileTransformer}
 *       so that every subsequently loaded class is passed through the JMC visitor pipeline.
 * </ol>
 *
 * <p>The agent arguments are parsed into an {@link AgentArgs} instance which drives both steps.
 */
public class InstrumentationAgent {

    /** Logger used to trace agent startup and report initialization failures. */
    private static final Logger LOGGER = LogManager.getLogger(InstrumentationAgent.class);

    /**
     * Loads the JMC runtime jar and makes its classes visible to the instrumented application.
     *
     * <p>The jar at {@code jmcRuntimeJarPath} is copied to a temporary file and appended to the
     * system class loader's search path via {@link Instrumentation#appendToSystemClassLoaderSearch}.
     * This must happen before any class is transformed, because the instrumentation inserts
     * references to JMC runtime types that would otherwise fail to resolve.
     *
     * @param inst the {@link Instrumentation} instance supplied by the JVM to the agent
     * @param jmcRuntimeJarPath the filesystem path to the JMC runtime jar (see {@link
     *     AgentArgs#getJmcRuntimeJarPath()})
     * @throws RuntimeException if the jar cannot be read or appended to the class loader search
     */
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
     * Bootstraps the JMC agent. Invoked by the JVM before the application's {@code main} method.
     *
     * <p>It parses {@code agentArgs} into an {@link AgentArgs}, loads the JMC runtime jar (see {@link
     * #loadDependencyJars}), and registers a {@link PremainInstrumentor} transformer (with {@code
     * canRetransform = true}) that applies the instrumentation to every matching class as it is
     * loaded. A failure to construct or register the transformer is logged and rethrown as a {@link
     * RuntimeException} so the run fails fast instead of proceeding without instrumentation.
     *
     * @param agentArgs the raw agent argument string passed after the jar path in the {@code
     *     -javaagent} flag (may be {@code null}); parsed by {@link AgentArgs}
     * @param inst the {@link Instrumentation} instance provided by the JVM, used to load the runtime
     *     jar and to register the class transformer
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        AgentArgs args = new AgentArgs(agentArgs);
        LOGGER.debug("Starting JMC agent");
        LOGGER.info("Arguments: {}", agentArgs);
        loadDependencyJars(inst, args.getJmcRuntimeJarPath());

        try {
            PremainInstrumentor instrumentor = new PremainInstrumentor(args);
            inst.addTransformer(instrumentor, true);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize JMC agent", e);
            System.err.println("Failed to initialize JMC agent: " + e.getMessage());
            throw new RuntimeException("Failed to initialize JMC agent", e);
        }
    }
}
