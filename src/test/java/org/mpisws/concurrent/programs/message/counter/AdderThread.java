package org.mpisws.concurrent.programs.message.counter;

import org.mpisws.util.concurrent.JMCThread;
import org.mpisws.util.concurrent.MessageServer;

public class AdderThread extends JMCThread {

  final long INC = 100;

  long counter_tid;

  @Override
  public void run() {
    MessageServer.send_tagged_msg(counter_tid, INC, 2);
  }
}
