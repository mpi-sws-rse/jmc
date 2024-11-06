package org.mpisws.runtime;

import executionGraph.ExecutionGraph;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class GraphQueue implements GraphCollection {
  private final Queue<ExecutionGraph> queue;

  public GraphQueue() {
    this.queue = new LinkedList<>();
  }

  @Override
  public void addGraph(ExecutionGraph g) {
    queue.add(g);
  }

  @Override
  public ExecutionGraph nextGraph() {
    return queue.poll();
  }

  @Override
  public boolean isEmpty() {
    return queue.isEmpty();
  }

  /**
   * @param graphs
   */
  @Override
  public void addAllGraphs(ArrayList<ExecutionGraph> graphs) {
    queue.addAll(graphs);
  }
}
