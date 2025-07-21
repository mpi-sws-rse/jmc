package org.mpisws.jmc.util;

/**
 * Utility class for exception handling.
 *
 * <p>This class provides methods to check if a given Throwable is an AssertionError or contains an
 * AssertionError in its cause chain.
 */
public class ExceptionUtil {

    /**
     * Checks if the given Throwable is an AssertionError or contains an AssertionError in its cause
     * chain.
     *
     * @param t the Throwable to check
     * @return true if the Throwable is an AssertionError or contains one in its cause chain, false
     *     otherwise
     */
    public static boolean isAssertionError(Throwable t) {
        if (t instanceof AssertionError) {
            return true;
        }
        if (t instanceof RuntimeException) {
            Throwable firstCause = t.getCause();
            if (firstCause != null) {
                return firstCause.getCause() instanceof AssertionError;
            }
        }
        return false;
    }
}
