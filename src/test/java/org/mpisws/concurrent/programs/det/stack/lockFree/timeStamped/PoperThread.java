package org.mpisws.concurrent.programs.det.stack.lockFree.timeStamped;

import org.mpisws.concurrent.programs.det.stack.Stack;
import org.mpisws.util.concurrent.JMCInterruptException;

public class PoperThread extends Thread {

  public Stack<Integer> stack;

  public PoperThread(Stack<Integer> stack) {
    this.stack = stack;
  }

  public PoperThread() {}

  @Override
  public void run() {
    try {
      stack.pop();
    } catch (JMCInterruptException e) {
      System.out.println("Interrupted");
    }
  }
}
