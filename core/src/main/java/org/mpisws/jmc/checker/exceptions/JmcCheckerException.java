package org.mpisws.jmc.checker.exceptions;

public class JmcCheckerException extends Exception {
    public JmcCheckerException(String message) {
        super(message);
    }

    public JmcCheckerException(String message, Throwable cause) {
        super(message, cause);
    }
}
