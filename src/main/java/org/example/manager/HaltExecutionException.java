package org.example.manager;

/**
 * The HaltExecutionException class is a serializable class that is used to signal the end of the model checking process.
 * The class contains a message to indicate the reason for the halt.
 */
public class HaltExecutionException extends Exception{

    /**
     * @property {@link #message} - A message to indicate the reason for the halt
     */
    private String message;

    /**
     * The default constructor initializes the class with a message set to null
     */
    public HaltExecutionException() {
        super();
    }

    /**
     * The following constructor initializes the class with a message
     *
     * @param message - A message to indicate the reason for the halt
     */
    public HaltExecutionException(String message) {
        super(message);
        this.message = message;
    }

    /**
     * The following constructor initializes the class with a message and a cause
     *
     * @param message - A message to indicate the reason for the halt
     * @param cause   - The cause of the halt
     */
    public HaltExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    /**
     * Returns the message to indicate the reason for the halt
     *
     * @return A message to indicate the reason for the halt
     */
    public String getMessage() {
        return message;
    }
}