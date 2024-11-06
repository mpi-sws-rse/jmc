package org.mpisws.concurrent.programs.tagged.message;

import org.mpisws.util.concurrent.JMCThread;
import org.mpisws.util.concurrent.MessageServer;

public class ReceiverThread extends JMCThread {
  long knownThread;
  long knownTag = 100;

  @Override
  public void run() {
    long myTid = Thread.currentThread().getId();
    Object value = MessageServer.recv_tagged_msg((tid, tag) -> tag == knownTag && tid == myTid);
    System.out.println("Value of the message is : " + value);
  }
}
