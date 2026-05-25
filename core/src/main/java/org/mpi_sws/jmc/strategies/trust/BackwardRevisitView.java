package org.mpi_sws.jmc.strategies.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula;
import org.mpi_sws.jmc.runtime.HaltCheckerException;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.solver.SolverUtil;
import org.mpi_sws.jmc.solver.incremental.IncrementalSolver;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Represents a restricted view of the execution graph. Some nodes are removed and some relations
 * are updated.
 */
public class BackwardRevisitView {
    private static final Logger LOGGER = LogManager.getLogger(BackwardRevisitView.class);
    private final ExecutionGraph graph;
    private final HashSet<Event.Key> removedNodes;
    private final HashSet<Event.Key> removedSymNodes;
    private final ExecutionGraphNode read;
    private final ExecutionGraphNode write;

    // Additional event, maintained here for revisits of a write exclusive with a read exclusive.
    // The write exclusive of the revisited read exclusive is stored here.
    private Event addEvent;

    /**
     * Creates a new backward revisit view.
     *
     * @param graph The execution graph.
     * @param read  The read event.
     * @param write The write event.
     */
    public BackwardRevisitView(
            ExecutionGraph graph, ExecutionGraphNode read, ExecutionGraphNode write) {
        this.graph = graph.clone();
        this.removedNodes = new HashSet<>();
        if (SolverUtil.getSolver() != null) {
            removedSymNodes = new HashSet<>();
        } else {
            removedSymNodes = null;
        }
        if (EventUtils.isLockAcquireRead(read.getEvent())) {
            // The read event is the additional event to be added
            // When revisiting this backward revisit
            // So we also mark it to be removed
            this.addEvent = read.getEvent().clone();
            this.removedNodes.add(read.key());
        }
        try {
            this.read = this.graph.getEventNode(read.key());
            this.write = this.graph.getEventNode(write.key());
            // When constructing a backward revisit of a write to a
            // lock acquire read, the write cannot be ever removed from the graph.
            // So we mark it as such.
            // Leads to a cyclic exploration otherwise.
            if (EventUtils.isLockAcquireRead(read.getEvent())) {
                EventUtils.markLockWriteFinal(this.write.getEvent());
            }
        } catch (NoSuchEventException ignored) {
            throw HaltCheckerException.error("The read or write event is not found.");
        }
    }

    public ExecutionGraphNode getWrite() {
        return write;
    }

    public ExecutionGraphNode getRead() {
        return read;
    }

    /**
     * Just marks the node as removed, does not update the graph
     */
    public void removeNode(Event.Key key) {
        removedNodes.add(key);
    }

    public boolean isMaximalSymbolicExtension() {
        IncrementalSolver solver = SolverUtil.getIncrementalSolver();
        if (solver == null || removedSymNodes == null) {
            throw new IllegalStateException("Solver is not initialized.");
        }

        if (removedSymNodes.isEmpty()) {
            return true;
        }

        // At this point the backward revisit view contains symbolic events among the deleted set. Thus,
        // we need to check their maximality condition.

        // First we need to reset the current solver's stack
        solver.resetCurrentProver();

        List<ExecutionGraphNode> symNodes = graph.getAllSymbolicEvents();
        if (symNodes.isEmpty()) {
            return true;
        }

        boolean maximal = true;

        // Then, we need to add all symbolic formulas among symbolic events in the graph, which are not in
        // the removed set, to the solver.
        for (ExecutionGraphNode node : symNodes) {
            if (removedSymNodes.contains(node.key())) {
                continue;
            }

            Event s =  node.getEvent();
            if (!s.isSymbolic()) {
                throw new IllegalStateException("The event is not symbolic.");
            }
            boolean result = s.getAttribute("result");
            JmcBooleanFormula formula = s.getAttribute("booleanFormula");
            if (result) {
                solver.addFormula(formula);
            } else {
                solver.addNegatedFormula(formula);
            }

        }

        // Next, we start checking the maximality of each symbolic event among deleted set.
        // We should do this check based on the insertion order of these events.
        for (ExecutionGraphNode node : symNodes) {
            if (removedNodes.contains(node.key())) {
                Event sym = node.getEvent();
                if (!sym.isSymbolic()) {
                    throw new IllegalStateException("The event is not symbolic.");
                }

                boolean SAT = false;
                boolean UNSAT = false;
                JmcBooleanFormula formula = sym.getAttribute("booleanFormula");
                boolean result = sym.getAttribute("result");

                SAT = solver.solveSymbolicFormula(formula);
                solver.pop();
                UNSAT = solver.disSolveSymbolicFormula(formula);
                solver.pop();

                if (SAT && UNSAT) {
                    if (!result) {
                        // We found a symbolic event which does not meet maximality condition. Thus, the revisit
                        // is not maximal.
                        maximal = false;
                        break;
                    }
                }

                // If we are here, then it means the current symbolic event is maximal and we can add it back to
                // the solver for checking the next symbolic event in the deleted set.
                if (SAT) {
                    solver.addFormula(formula);
                } else if (UNSAT) {
                    solver.addNegatedFormula(formula);
                } else {
                    throw new IllegalStateException(
                            "The symbolic formula is neither satisfiable nor unsatisfiable.");
                }
            }
        }

        // Finally, before returning the result, we should restore the solver's stack state. Thus, we need to
        // reset the solver's stack again and push back symbolic constraints in the execution graph
        solver.resetCurrentProver();
        for (ExecutionGraphNode node : symNodes) {
            Event sym = node.getEvent();
            if (!sym.isSymbolic()) {
                throw new IllegalStateException("The event is not symbolic.");
            }
            boolean result = sym.getAttribute("result");
            JmcBooleanFormula formula = sym.getAttribute("booleanFormula");

            if (result) {
                solver.addFormula(formula);
            }  else {
                solver.addNegatedFormula(formula);
            }
        }

        return maximal;
    }

    /**
     * Checks if the restricted view is a maximal extension
     *
     * <p>Meta: Breaks the separation of concerns. Is part of the core logic of the Trust algorithm
     */
    public boolean isMaximalExtension() {
        LOGGER.debug("Checking if the restricted view is a maximal extension");
        HashSet<Event.Key> nodesToCheck = new HashSet<>(this.removedNodes);
        nodesToCheck.add(read.key());
        try {
            for (Event.Key key : nodesToCheck) {
                ExecutionGraphNode node = graph.getEventNode(key);
                LOGGER.debug("Checking if the node is a maximal extension: " + node.getEvent());
                int nodeTOIndex = graph.getTOIndex(node);
                if (nodeTOIndex == -1) {
                    throw HaltExecutionException.error("The event does not have a TO index.");
                }
                if (node.getEvent().getType() == Event.Type.NOOP
                        || node.getEvent().getType() == Event.Type.ASSUME) {
                    continue;
                }

                if (EventUtils.isFinalLockWrite(node.getEvent())) {
                    return false;
                }
                
                Predicate<Event.Key> previous =
                        (k) -> {
                            try {
                                ExecutionGraphNode kNode = graph.getEventNode(k);
                                // Based on the definition of previous set in the TruSt paper,
                                // we need to check if the event TO-prefix of the node, or it is
                                // in the porf-prefix of the given write event.
                                return graph.getTOIndex(k) <= nodeTOIndex
                                        || kNode.happensBefore(write);
                            } catch (NoSuchEventException e) {
                                return false;
                            }
                        };
                // 1. Check first if key is a write event that has a dangling read in the
                // restricted graph.
                if (EventUtils.isWrite(node.getEvent())) {
                    List<Event.Key> reads = node.getSuccessors(Relation.ReadsFrom);
                    for (Event.Key readKey : reads) {
                        // Check if in previous
                        if (previous.test(readKey)) {
                            LOGGER.debug("The read event is in the previous set");
                            return false;
                        }
                    }
                }
                // 2. Check if the write event associated with the node is CO maximal in previous
                ExecutionGraphNode nodeWrite = node;
                if (EventUtils.isRead(node.getEvent())) {
                    List<Event.Key> writes = node.getPredecessors(Relation.ReadsFrom);
                    if (writes.size() != 1) {
                        throw HaltExecutionException.error(
                                "The read event does not have a valid rf event.");
                    }
                    nodeWrite = graph.getEventNode(writes.iterator().next());
                    LOGGER.debug(
                            "Checking if the write event is CO maximal: " + nodeWrite.getEvent());
                }
                if (!previous.test(nodeWrite.key())) {
                    LOGGER.debug("The write event is not in the previous set");
                    return false;
                }
                // Now node is a write event for sure
                // We only need to check if the CO after events for the same location are in
                // previous

                List<ExecutionGraphNode> writes;
                // We need to check if the nodeWrite is init or not
                //                if (nodeWrite.getEvent().getType() == Event.Type.INIT) {
                //                    // TODO :: This is not an efficient implementation. We need to
                // optimize this
                //                    writes = graph.getAllWrites();
                //                    for (ExecutionGraphNode writeNode : writes) {
                //                        if (previous.test(writeNode.key())) {
                //                            LOGGER.debug("The write event is in the previous
                // set");
                //                            return false;
                //                        }
                //                    }
                //                } else {
                Integer location = nodeWrite.getEvent().getLocation();
                if (location == null) {
                    // This is because nodeWrite is the init event
                    // We get the location from the read event then
                    location = node.getEvent().getLocation();
                }
                writes = graph.getWrites(location);
                int index = writes.indexOf(nodeWrite);
                if (index < writes.size() - 1) {
                    for (int i = index + 1; i < writes.size(); i++) {
                        if (previous.test(writes.get(i).key())) {
                            LOGGER.debug("The write event is in the previous set");
                            return false;
                        }
                    }
                }
                //                }
            }
        } catch (NoSuchEventException e) {
            throw HaltExecutionException.error("The event is not found.");
        }
        LOGGER.debug("The restricted view is a maximal extension");
        return true;
    }

    /**
     * Gets the restricted graph.
     *
     * @return The restricted graph
     */
    public ExecutionGraph getRestrictedGraph() {
        ExecutionGraph restrictedGraph = graph;
        // So far the coherency of this write is not tracked
        // TODO: Maybe this should be done in the constructor?
        // Update the reads-from relation
        restrictedGraph.changeReadsFrom(read, write);
        // Remove the nodes
        restrictedGraph.restrictBySet(removedNodes);
        restrictedGraph.recomputeVectorClocks();
        restrictedGraph.checkDanglingEdges();
        restrictedGraph.checkConsistency();
        return restrictedGraph;
    }

    public Event additionalEvent() {
        return addEvent;
    }
}
