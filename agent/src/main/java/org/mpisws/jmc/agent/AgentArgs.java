package org.mpisws.jmc.agent;

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
    private List<String> instrumentingPackages;
    private List<String> excludedPackages;
    private String jmcRuntimeJarPath;

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
                    }else if (parts[0].equals(EXCLUDED_PKG_FLAG)) {
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

    public boolean isDebug() {
        return debug;
    }

    public String getDebugSavePath() {
        return debugSavePath;
    }

    public List<String> getInstrumentingPackages() {
        return instrumentingPackages;
    }

    public List<String> getExcludedPackages() { return excludedPackages; }

    public String getJmcRuntimeJarPath() {
        return jmcRuntimeJarPath;
    }
}
