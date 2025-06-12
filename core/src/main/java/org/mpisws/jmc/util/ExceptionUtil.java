package org.mpisws.jmc.util;

public class ExceptionUtil {

    public static boolean isAssertionError(Throwable t) {
        if( t instanceof AssertionError) {
            return true;
        }
        if (t instanceof RuntimeException) {
            return t.getCause().getCause() instanceof AssertionError;
        }
        return false;
    }
}
