package org.mpisws.util.concurrent;

import java.util.concurrent.ThreadFactory;
import org.mpisws.runtime.RuntimeEnvironment;

public class JMCDependantThreadFactory implements ThreadFactory {

  public int id;

  public ThreadFactory userDefinedThreadFactory;

  public JMCDependantThreadFactory(ThreadFactory userDefinedThreadFactory, int id) {
    super();
    this.id = id;
    this.userDefinedThreadFactory = userDefinedThreadFactory;
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread newThread = userDefinedThreadFactory.newThread(r);
    JMCDependantStarterThread jmcDependantStarterThread =
        new JMCDependantStarterThread(newThread, id);
    RuntimeEnvironment.addThread(jmcDependantStarterThread);
    return jmcDependantStarterThread;
  }
}
