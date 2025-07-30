package org.mpi_sws.jmc.agent;

import java.util.ArrayList;
import java.util.List;

/** The AgentArgs class is used to parse the agent arguments. */
public class AgentArgs {
    private static final String DEBUG_FLAG = "debug";
    private static final String DEBUG_PATH_FLAG = "debugSavePath";
    private static final String INSTRUMENTING_PKG_FLAG = "instrumentingPackages";
    private static final String EXCLUDED_PKG_FLAG = "excludedPackages";
    private static final String JMC_RUNTIME_JAR_PATH_FLAG = "jmcRuntimeJarPath";
    private boolean debug = false;
    private String debugSavePath = "build/generated/instrumented";
    private List<String> instrumentingPackages = new ArrayList<>();
    private List<String> excludedPackages = new ArrayList<>();
    private String jmcRuntimeJarPath = "build/deps/jmc-0.1.1.jar";

    /**
     * The AgentArgs constructor is used to parse the agent arguments.
     *
     * @param agentArgs the agent arguments
     */
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
                    } else if (parts[0].equals(EXCLUDED_PKG_FLAG)) {
                        excludedPackages = List.of(parts[1].split(";"));
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

    /**
     * Checks if debug mode is enabled.
     *
     * @return true if debug mode is enabled, false otherwise
     */
    public boolean isDebug() {
        return debug;
    }

    /**
     * Gets the path where debug information will be saved.
     *
     * @return the debug save path
     */
    public String getDebugSavePath() {
        return debugSavePath;
    }

    /**
     * Gets the list of packages to instrument.
     *
     * @return the list of instrumenting packages
     */
    public List<String> getInstrumentingPackages() {
        return instrumentingPackages;
    }

    /**
     * Gets the list of packages to exclude from instrumentation.
     *
     * @return the list of excluded packages
     */
    public List<String> getExcludedPackages() {
        return excludedPackages;
    }

    /**
     * Gets the path to the JMC runtime jar.
     *
     * @return the path to the JMC runtime jar
     */
    public String getJmcRuntimeJarPath() {
        return jmcRuntimeJarPath;
    }
}
