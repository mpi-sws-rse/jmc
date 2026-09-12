package org.mpi_sws.jmc.agent;

import java.util.List;

/**
 * Decides which classes the JMC agent should instrument.
 *
 * <p>{@link PremainInstrumentor#transform} consults a {@code JmcMatcher} before running the visitor
 * pipeline: only classes for which {@link #matches} returns {@code true} are transformed. The matcher
 * unconditionally filters out JVM/framework classes and the JMC agent's own classes, then applies the
 * user-supplied {@code excludedPackages} / {@code instrumentingPackages} scope from {@link AgentArgs}.
 */
public class JmcMatcher {

    /** Logger for diagnostic messages about matching decisions. */
    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger(JmcMatcher.class);

    /** Package prefixes to instrument; when empty, every non-filtered class matches. */
    private final List<String> matchingPackages;
    /** Package prefixes to exclude; a class whose name starts with any of these never matches. */
    private final List<String> excludedPackages;

    /**
     * Constructs a new JmcMatcher with the specified matching and excluded packages.
     *
     * @param matchingPackages the list of packages to match
     * @param excludedPackages the list of packages to exclude
     */
    public JmcMatcher(List<String> matchingPackages, List<String> excludedPackages) {
        this.matchingPackages = matchingPackages;
        this.excludedPackages = excludedPackages;
    }

    /**
     * Determines whether the given class should be instrumented.
     *
     * <p>The internal class name (slash-separated) is first converted to a dotted type name, then
     * tested against three filters in order:
     *
     * <ol>
     *   <li><b>Infrastructure exclusion:</b> classes in JVM/framework packages ({@code java.},
     *       {@code javax.}, {@code sun.}, {@code com.sun.}, {@code jdk.}, {@code kotlin.},
     *       {@code kotlinx.}, {@code org.gradle.}, {@code org.slf4j.}, {@code worker.org.gradle.},
     *       {@code org.junit.}) and the JMC agent's own package ({@code org.mpi_sws.jmc.agent.}) never
     *       match.
     *   <li><b>User exclusion:</b> a class whose dotted name starts with any prefix in {@link
     *       #excludedPackages} never matches.
     *   <li><b>User scope:</b> if {@link #matchingPackages} is non-empty, the class matches only when
     *       its dotted name starts with one of those prefixes; if it is empty, any class surviving the
     *       previous filters matches.
     * </ol>
     *
     * @param className the internal (slash-separated) name of the class being loaded, e.g. {@code
     *     "com/example/Foo"}
     * @param classLoader the defining class loader of the class (currently unused by the matching
     *     logic)
     * @return {@code true} if the class should be instrumented, {@code false} otherwise
     */
    public boolean matches(String className, ClassLoader classLoader) {
        String typeName = className.replace("/", ".");
        if (typeName.startsWith("java.")
                || typeName.startsWith("javax.")
                || typeName.startsWith("sun.")
                || typeName.startsWith("com.sun.")
                || typeName.startsWith("jdk.")
                || typeName.startsWith("kotlin.")
                || typeName.startsWith("kotlinx.")
                || typeName.startsWith("org.gradle.")
                || typeName.startsWith("org.slf4j.")
                || typeName.startsWith("worker.org.gradle.")
                || typeName.startsWith("org.junit.")) {
            return false;
        }
        // Exclude instrumentation classes.
        if (typeName.startsWith("org.mpi_sws.jmc.agent.")) {
            return false;
        }
        // Exclude instrumentation classes.
        if (!excludedPackages.isEmpty()) {
            for (String exclude : excludedPackages) {
                if (!exclude.isEmpty() && typeName.startsWith(exclude)) {
                    //LOGGER.debug(
                      //      "Excluding class: {} due to excluded package: {}", typeName, exclude);
                    return false;
                }
            }
        }
        if (!matchingPackages.isEmpty()) {
            return matchingPackages.stream().anyMatch(typeName::startsWith);
        } else {
            return true;
        }
    }
}
