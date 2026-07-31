package org.mpi_sws.jmc.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses and holds the configuration passed to the JMC agent on the {@code -javaagent} flag.
 *
 * <p>The agent argument string is the text after the jar path, e.g. {@code
 * -javaagent:agent.jar=debug=true,instrumentingPackages=com.example}. It is a comma-separated list
 * of tokens; each token is either a {@code key=value} pair or the bare flag {@code debug}. List-valued
 * options ({@code instrumentingPackages}, {@code excludedPackages}) use a semicolon-separated value.
 * Unrecognized keys are ignored.
 *
 * <p>An {@code AgentArgs} instance is built once in {@link InstrumentationAgent#premain} and consumed
 * by {@link PremainInstrumentor} (via {@link JmcMatcher}) to decide which classes to instrument and
 * how to log them.
 */
public class AgentArgs {
    /** Argument key for the debug flag; enables dumping instrumented classes. */
    private static final String DEBUG_FLAG = "debug";
    /** Argument key for the directory where instrumented classes are saved in debug mode. */
    private static final String DEBUG_PATH_FLAG = "debugSavePath";
    /** Argument key for the semicolon-separated list of package prefixes to instrument. */
    private static final String INSTRUMENTING_PKG_FLAG = "instrumentingPackages";
    /** Argument key for the semicolon-separated list of package prefixes to exclude. */
    private static final String EXCLUDED_PKG_FLAG = "excludedPackages";
    /** Argument key for the path to the JMC runtime jar. */
    private static final String JMC_RUNTIME_JAR_PATH_FLAG = "jmcRuntimeJarPath";

    /** Whether debug mode is enabled; when {@code true}, instrumented classes are written to disk. */
    private boolean debug = false;
    /** Directory in which instrumented {@code .class} files are saved when {@link #debug} is on. */
    private String debugSavePath = "build/generated/instrumented";
    /** Package prefixes defining the instrumentation scope; empty means "instrument everything". */
    private List<String> instrumentingPackages = new ArrayList<>();
    /** Package prefixes to exclude from instrumentation; a class matching any prefix is skipped. */
    private List<String> excludedPackages = new ArrayList<>();
    /** Path to the JMC runtime jar loaded before instrumentation begins. */
    private String jmcRuntimeJarPath = "build/deps/jmc-0.1.1.jar";

    /**
     * Parses the raw agent argument string, populating the option fields.
     *
     * <p>When {@code agentArgs} is non-{@code null} it is split on commas into tokens; each token is
     * then split on {@code =}. A two-part token sets the matching option ({@code debug},
     * {@code debugSavePath}, {@code instrumentingPackages}, {@code excludedPackages},
     * {@code jmcRuntimeJarPath}), with the list options further split on {@code ;}. A single-part
     * token equal to {@code debug} enables debug mode. Any option not present keeps its default.
     *
     * @param agentArgs the raw agent argument string (may be {@code null}, in which case all defaults
     *     are kept)
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

    /**
     * Returns a human-readable representation of all parsed options, used for debug logging of the
     * agent configuration.
     *
     * @return a string listing every option and its current value
     */
    public String toString() {
        return "AgentArgs{"
                + "debug="
                + debug
                + ", debugSavePath='"
                + debugSavePath
                + '\''
                + ", instrumentingPackages="
                + instrumentingPackages
                + ", excludedPackages="
                + excludedPackages
                + ", jmcRuntimeJarPath='"
                + jmcRuntimeJarPath
                + '\''
                + '}';
    }
}
