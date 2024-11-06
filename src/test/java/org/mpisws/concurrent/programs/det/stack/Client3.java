package org.mpisws.concurrent.programs.det.stack;

import java.util.ArrayList;
import java.util.List;
import org.mpisws.concurrent.programs.det.stack.lockFree.elimination.EliminationBackoffStack;

public class Client3 {

  public static void main(String[] args) {
    Stack stack = new EliminationBackoffStack<Integer>();
    int NUM_OPERATIONS = 3;

    List<Integer> items = new ArrayList<>(NUM_OPERATIONS);
    for (int i = 0; i < NUM_OPERATIONS; i++) {
      items.add(i);
    }

    List<InsertionThread> threads = new ArrayList<>(NUM_OPERATIONS);
    for (int i = 0; i < NUM_OPERATIONS; i++) {
      Integer item = items.get(i);
      InsertionThread thread = new InsertionThread();
      thread.stack = stack;
      thread.item = item;
      threads.add(thread);
    }

    for (int i = 0; i < NUM_OPERATIONS; i++) {
      threads.get(i).start();
    }

    for (int i = 0; i < NUM_OPERATIONS; i++) {
      try {
        threads.get(i).join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    System.out.println("Insertion Finished");
  }
}
