package org.mpisws.jmc.runtime;

/** Exception thrown to halt execution of the current and all subsequent executions. */
public class HaltCheckerException extends RuntimeException {
    private boolean okay = false;
    private boolean timeout = false;

    private HaltCheckerException(boolean ok, String message, boolean timeout) {
        super(message);
        this.okay = ok;
        this.timeout = timeout;
    }

    public static HaltCheckerException ok() {
        return new HaltCheckerException(true, "OK", false);
    }

    public static HaltCheckerException timeout() {
        return new HaltCheckerException(false, "Timeout", true);
    }

    public static HaltCheckerException error(String message) {
        return new HaltCheckerException(false, message, false);
    }

    public boolean isOkay() {
        return okay;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
