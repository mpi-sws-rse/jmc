package org.mpisws.runtime;

/**
 * Exception thrown to halt execution of the current and all subsequent executions.
 */
public class HaltCheckerException extends RuntimeException {
    private boolean okay = false;

    public HaltCheckerException() {
        super();
        this.okay = true;
    }

    public HaltCheckerException(String message) {
        super(message);
    }

    public boolean isOkay() {
        return okay;
    }
}
