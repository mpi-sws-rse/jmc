package org.mpisws.jmc.agent;

import java.util.List;

/**
 * Matcher for JMC agent.
 *
 * <p>Currently filters out classes loaded by built-in class loader.
 */
public class JmcMatcher {

    private List<String> matchingPackages;

    public JmcMatcher(List<String> matchingPackages) {
        this.matchingPackages = matchingPackages;
    }

    /**
     * Matches the class name.
     *
     * @param className the class name
     * @param classLoader the class loader
     * @return true if the class name matches
     */
    public boolean matches(String className, ClassLoader classLoader) {
        //        if (classLoader == null) {
        //            return false;
        //        }
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
        if (typeName.startsWith("org.mpisws.jmc.agent.")) {
            return false;
        }
        if (!matchingPackages.isEmpty()) {
            return matchingPackages.stream().anyMatch(typeName::startsWith);
        } else {
            return true;
        }
    }
}
