package org.mpisws.jmc.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.mpisws.jmc.agent.visitors.JmcFutureVisitor;
import org.mpisws.jmc.agent.visitors.JmcReadWriteVisitor;
import org.mpisws.jmc.agent.visitors.JmcReentrantLockVisitor;
import org.mpisws.jmc.agent.visitors.JmcThreadVisitor;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.jar.JarFile;

/**
 * The InstrumentationAgent class is the entry point for the instrumentation agent. It is used to
 * set up the agent and install the instrumentation on the target application.
 */
public class InstrumentationAgent {
    /** The AgentArgs class is used to parse the agent arguments. */
    private static class AgentArgs {
        private static final String DEBUG_FLAG = "debug";
        private static final String DEBUG_PATH_FLAG = "debugSavePath";
        private static final String INSTRUMENTING_PKG_FLAG = "instrumentingPackages";
        private static final String JMC_RUNTIME_JAR_PATH_FLAG = "jmcRuntimeJarPath";
        private boolean debug = false;
        private String debugSavePath = "build/generated/instrumented";
        private List<String> instrumentingPackages;
        private String jmcRuntimeJarPath;

        public AgentArgs(String agentArgs) {
            if (agentArgs != null) {
                String[] args = agentArgs.split(",");
                for (String arg : args) {
                    String[] parts = arg.split("=");
                    if (parts.length == 2) {
                        if (parts[0].equals(DEBUG_FLAG)) {
                            debug = Boolean.parseBoolean(parts[1]);
                        } else if (parts[0].equals(DEBUG_PATH_FLAG)) {
                            debugSavePath = parts[1];
                        } else if (parts[0].equals(INSTRUMENTING_PKG_FLAG)) {
                            instrumentingPackages = List.of(parts[1].split(";"));
                        } else if (parts[0].equals(JMC_RUNTIME_JAR_PATH_FLAG)) {
                            jmcRuntimeJarPath = parts[1];
                        }
                    } else {
                        if (arg.equals(DEBUG_FLAG)) {
                            debug = true;
                        }
                    }
                }
            }
        }

        public boolean isDebug() {
            return debug;
        }

        public String getDebugSavePath() {
            return debugSavePath;
        }

        public List<String> getInstrumentingPackages() {
            return instrumentingPackages;
        }

        public String getJmcRuntimeJarPath() {
            return jmcRuntimeJarPath;
        }
    }

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
     * The premain method is called before the application's main method is called. It is used to
     * set up the instrumentation agent.
     *
     * @param agentArgs the agent arguments
     * @param inst the instrumentation object
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        loadDependencyJars(inst);
        AgentArgs args = new AgentArgs(agentArgs);
        AgentBuilder agentBuilder = new AgentBuilder.Default();
        if (args.isDebug()) {
            agentBuilder = agentBuilder.with(new DebugListener(args.getDebugSavePath()));
        }
        agentBuilder
                .with(AgentBuilder.InjectionStrategy.UsingReflection.INSTANCE)
                .type(new JmcMatcher(args.getInstrumentingPackages()))
                .transform(
                        new AgentBuilder.Transformer() {
                            @Override
                            public DynamicType.Builder<?> transform(
                                    DynamicType.Builder<?> builder,
                                    TypeDescription typeDescription,
                                    ClassLoader classLoader,
                                    JavaModule javaModule,
                                    ProtectionDomain protectionDomain) {
                                return builder.visit(new JmcThreadVisitor())
                                        .visit(new JmcReadWriteVisitor())
                                        .visit(new JmcReentrantLockVisitor())
                                        .visit(new JmcFutureVisitor());
                            }
                        })
                .installOn(inst);
    }
}
