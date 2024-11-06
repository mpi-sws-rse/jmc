package org.mpisws.runtime;

import executionGraph.ExecutionGraph;
import java.util.ArrayList;
import java.util.Stack;

public class GraphStack implements GraphCollection {
  private final Stack<ExecutionGraph> stack;

  public GraphStack() {
    this.stack = new Stack<>();
  }

  @Override
  public void addGraph(ExecutionGraph g) {
    stack.push(g);
  }

  @Override
  public ExecutionGraph nextGraph() {
    return stack.pop();
  }

  @Override
  public boolean isEmpty() {
    return stack.isEmpty();
  }

  /**
   * @param graphs
   */
  @Override
  public void addAllGraphs(ArrayList<ExecutionGraph> graphs) {
    stack.addAll(graphs);
  }
}
