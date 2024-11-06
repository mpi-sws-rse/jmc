package org.mpisws.util.concurrent;

import org.mpisws.manager.HaltExecutionException;
import org.mpisws.runtime.RuntimeEnvironment;

public class JMCInterruptException extends Exception {

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

  public void terminateThread() throws HaltExecutionException {
    RuntimeEnvironment.finishThreadRequest(Thread.currentThread());
  }
}
