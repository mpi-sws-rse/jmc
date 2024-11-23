package org.mpisws.util.concurrent;


public class JMCInterruptException extends RuntimeException {

    private String message;

    public JMCInterruptException() {
        super();
    }

    public JMCInterruptException(String message) {
        super(message);
        this.message = message;
    }

    public JMCInterruptException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
