package org.mpisws.runtime;

import executionGraph.ExecutionGraph;
import java.util.ArrayList;

public interface GraphCollection {

  void addGraph(ExecutionGraph g);

  ExecutionGraph nextGraph();

  boolean isEmpty();

  void addAllGraphs(ArrayList<ExecutionGraph> graphs);
}
