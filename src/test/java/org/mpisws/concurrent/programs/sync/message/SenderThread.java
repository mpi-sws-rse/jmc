package org.mpisws.concurrent.programs.sync.message;

import org.mpisws.util.concurrent.JMCThread;
import org.mpisws.util.concurrent.MessageServer;

public class SenderThread extends JMCThread {

  ReceiverThread receiver;

  @Override
  public void context() {
    MessageServer.send_tagged_msg(receiver.getId(), 10, "hello");
  }
}
