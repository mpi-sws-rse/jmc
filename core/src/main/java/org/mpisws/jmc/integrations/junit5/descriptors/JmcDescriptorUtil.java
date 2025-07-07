package org.mpisws.jmc.integrations.junit5.descriptors;

import org.mpisws.jmc.annotations.strategies.JmcTrustStrategy;
import org.mpisws.jmc.checker.JmcCheckerConfiguration;
import org.mpisws.jmc.strategies.trust.TrustStrategy;

import java.lang.reflect.Method;

/**
 * Utility class for handling JMC descriptor configurations.
 *
 * <p>This class provides methods to check and update JMC checker configurations based on
 * annotations present on classes or methods.
 */
public class JmcDescriptorUtil {

    /**
     * Checks the provided class and method for JMC trust strategy annotations and updates the JMC
     * checker configuration builder accordingly.
     *
     * @param builder The JMC checker configuration builder to update.
     * @param clazz The class to check for annotations.
     * @param method The method to check for annotations.
     * @return An updated JMC checker configuration builder.
     */
    public static JmcCheckerConfiguration.Builder checkStrategyConfig(
            JmcCheckerConfiguration.Builder builder, Class<?> clazz, Method method) {
        if (method != null && hasStrategyAnnotation(method)) {
            return updateBuilderFromAnnotation(builder, method);
        } else if (clazz != null && hasStrategyAnnotation(clazz)) {
            return updateBuilderFromAnnotation(builder, clazz);
        } else {
            return builder;
        }
    }

    private static boolean hasStrategyAnnotation(Method method) {
        return method.isAnnotationPresent(JmcTrustStrategy.class);
    }

    private static boolean hasStrategyAnnotation(Class<?> clazz) {
        return clazz.isAnnotationPresent(JmcTrustStrategy.class);
    }

    private static JmcCheckerConfiguration.Builder updateBuilderFromAnnotation(
            JmcCheckerConfiguration.Builder builder, Method method) {
        JmcTrustStrategy annotation = method.getAnnotation(JmcTrustStrategy.class);
        return updateBuilderWithTrust(builder, annotation);
    }

    private static JmcCheckerConfiguration.Builder updateBuilderFromAnnotation(
            JmcCheckerConfiguration.Builder builder, Class<?> clazz) {
        JmcTrustStrategy annotation = clazz.getAnnotation(JmcTrustStrategy.class);
        return updateBuilderWithTrust(builder, annotation);
    }

    private static JmcCheckerConfiguration.Builder updateBuilderWithTrust(
            JmcCheckerConfiguration.Builder builder, JmcTrustStrategy annotation) {
        return builder.strategyConstructor(
                (config) -> {
                    long seed = config.getSeed();
                    if (annotation.seed() != 0L) {
                        seed = annotation.seed();
                    }
                    return new TrustStrategy(
                            seed,
                            annotation.schedulingPolicy(),
                            annotation.debug(),
                            annotation.reportPath());
                });
    }
}
