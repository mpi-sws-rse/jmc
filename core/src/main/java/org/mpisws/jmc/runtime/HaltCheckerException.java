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

    private HaltCheckerException(String message, Throwable cause) {
        super(message, cause);
        this.okay = false;
        this.timeout = false;
    }

    /**
     * Constructs a new {@link HaltCheckerException} indicating that the exploration stopped
     * naturally without any errors.
     */
    public static HaltCheckerException ok() {
        return new HaltCheckerException(true, "OK", false);
    }

    /**
     * Constructs a new {@link HaltCheckerException} indicating that the exploration was halted due
     * to a timeout.
     */
    public static HaltCheckerException timeout() {
        return new HaltCheckerException(false, "Timeout", true);
    }

    /**
     * Constructs a new {@link HaltCheckerException} indicating that the exploration was halted due
     * to an error.
     *
     * @param message the error message
     */
    public static HaltCheckerException error(String message) {
        return new HaltCheckerException(false, message, false);
    }

    /**
     * Constructs a new {@link HaltCheckerException} indicating that the exploration was halted due
     * to an error, with a cause.
     *
     * @param message the error message
     * @param cause the cause of the error
     */
    public static HaltCheckerException error(String message, Throwable cause) {
        return new HaltCheckerException(message, cause);
    }

    /**
     * Returns whether the exploration was successful without any errors.
     *
     * @return true if the exploration was successful, false otherwise
     */
    public boolean isOkay() {
        return okay;
    }

    /**
     * Returns whether the exploration was halted due to a timeout.
     *
     * @return true if the exploration was halted due to a timeout, false otherwise
     */
    public boolean isTimeout() {
        return timeout;
    }
}
