package org.mpisws.util.concurrent;

import java.util.concurrent.ThreadFactory;
import org.mpisws.runtime.RuntimeEnvironment;

public class JMCSimpleThreadFactory implements ThreadFactory {

  int id;

  public JMCSimpleThreadFactory(int id) {
    super();
    this.id = id;
  }

  @Override
  public Thread newThread(Runnable r) {
    JMCStarterThread newThread = new JMCStarterThread(r, id);
    RuntimeEnvironment.addThread(newThread);
    return newThread;
  }
}
