package org.mpisws.checker.strategy;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import dpor.Trust;
import executionGraph.CO;
import executionGraph.ExecutionGraph;
import executionGraph.Node;
import kotlin.Pair;
import org.mpisws.checker.SearchStrategy;
import org.mpisws.runtime.RuntimeEnvironment;
import programStructure.*;

/**
 * The TrustStrategy class implements the {@link SearchStrategy} interface and is responsible for managing the execution
 * order of events in a multithreaded program using the {@link Trust} model checker. It maintains a record of execution
 * graphs and a trust object for the trust model checker. The class provides functionality to handle various types of
 * events including start, join, read, write, and finish events. The class uses the {@link RuntimeEnvironment} API to
 * create and record events. The TrustStrategy class is designed to control the flow of a program's
 * execution by using trust model checker. When it faces a new event, it passes the event to the trust model checker and
 * updates the current graph based on the model checker's response. Based on the response, it picks the next thread to
 * execute. The class also provides functionality to save the execution graphs to a file.
 */
public class TrustStrategy implements SearchStrategy {

    /**
     * @property {@link #trust} is used to store the trust object that is used to call the model checker.
     */
    public Trust trust;

    /**
     * @property {@link #mcGraphs} is used to store the execution graphs that are generated by the model checker.
     */
    public List<ExecutionGraph> mcGraphs;

    /**
     * @property {@link #currentGraph} is used to store the current execution graph.
     */
    public ExecutionGraph currentGraph;

    /**
     * @property {@link #guidingActivate} is used to indicate whether the {@link #guidingExecutionGraph} is available or
     * not.
     */
    private boolean guidingActivate = false;

    /**
     * @property {@link #guidingExecutionGraph} is used to store the guiding execution graph.
     */
    private ExecutionGraph guidingExecutionGraph;

    /**
     * @property {@link #guidingNode} is used to store the current node of the {@link #guidingExecutionGraph}.
     */
    private Node guidingNode;

    /**
     * @property {@link #guidingThread} is used to store the current active thread of the {@link #guidingExecutionGraph}.
     */
    private int guidingThread;

    /**
     * @property {@link #guidingEvents} is used to store the events that are available in the {@link #guidingExecutionGraph}.
     */
    private List<Event> guidingEvents;

    /**
     * @property {@link #guidingEvent} is used to store the current event of the {@link #guidingExecutionGraph}.
     */
    private Event guidingEvent;

    /**
     * @property {@link #buggyTracePath} is used to store the path of the buggy trace file.
     */
    private final String buggyTracePath;

    /**
     * @property {@link #buggyTraceFile} is used to store the name of the buggy trace file.
     */
    private final String buggyTraceFile;

    /**
     * @property {@link #executionGraphsPath} is used to store the path of the execution graphs directory.
     */
    private final String executionGraphsPath;

    /**
     * The following constructor initializes the model checker graphs list, the current execution graph, trust object,
     * the guiding execution graph if it is available, and the guiding events list.
     */
    public TrustStrategy() {
        buggyTracePath = RuntimeEnvironment.buggyTracePath;
        buggyTraceFile = RuntimeEnvironment.buggyTraceFile;
        executionGraphsPath = RuntimeEnvironment.executionGraphsPath;
        if (!Files.exists(Paths.get(executionGraphsPath))) {
            System.out.println("[Trust Strategy Message] : Directory " + executionGraphsPath + " does not exist ");
            System.exit(0);
        }
        if (!Files.exists(Paths.get(buggyTracePath))) {
            System.out.println("[Trust Strategy Message] : Directory " + buggyTracePath + " does not exist ");
            System.exit(0);
        }
        initMcGraphs();
        initCurrentGraph();
        initTrust();
        initGuidingGraph();
    }

    /**
     * Initializes the model checker graphs list.
     * <p>
     * This method initializes the {@link RuntimeEnvironment#mcGraphs} list, {@link RuntimeEnvironment#tempMcGraphs} list,
     * and {@link #mcGraphs} list.
     * </p>
     */
    private void initMcGraphs() {
        if (RuntimeEnvironment.mcGraphs == null) {
            RuntimeEnvironment.mcGraphs = new ArrayList<>();
        }
        RuntimeEnvironment.tempMcGraphs = new ArrayList<>();
        mcGraphs = new ArrayList<>();
    }

    /**
     * Initializes the current execution graph.
     * <p>
     * This method initializes the {@link #currentGraph} with the {@link InitializationEvent}.
     * </p>
     */
    private void initCurrentGraph() {
        currentGraph = new ExecutionGraph();
        currentGraph.addRoot(new InitializationEvent());
    }

    /**
     * Initializes the trust object.
     * <p>
     * This method initializes the {@link #trust} object. It sets the {@link Trust#getGraphCounter()} with the number of
     * graphs that are available in the {@link RuntimeEnvironment#mcGraphs}.
     * </p>
     */
    private void initTrust() {
        trust = new Trust(executionGraphsPath);
        trust.setGraphCounter(RuntimeEnvironment.numOfGraphs);
    }

    /**
     * Initializes the guiding execution graph.
     * <p>
     * This method initializes the {@link #guidingExecutionGraph} if the {@link RuntimeEnvironment#mcGraphs} is not empty.
     * Otherwise, it initializes the {@link #trust} object with the root event of the {@link #currentGraph}.
     * </p>
     */
    private void initGuidingGraph() {
        if (!RuntimeEnvironment.mcGraphs.isEmpty()) {
            loadGuidingGraph();
        } else {
            noGuidingGraph();
        }
    }

    /**
     * Loads the guiding execution graph.
     * <p>
     * This method loads the guiding execution graph from the first element of the {@link RuntimeEnvironment#mcGraphs}
     * list, sets the {@link #guidingActivate} to true, initializes the {@link #guidingNode} with the root of the
     * {@link #guidingExecutionGraph}, sets the {@link #guidingThread} to 0, and initializes the {@link #guidingEvents}.
     * </p>
     */
    private void loadGuidingGraph() {
        System.out.println("[Trust Strategy Message] : The RuntimeEnvironment has a guiding execution graph");
        guidingActivate = true;
        guidingExecutionGraph = RuntimeEnvironment.mcGraphs.remove(0);
        guidingNode = guidingExecutionGraph.getRoot();
        guidingThread = 0;
        guidingEvents = new ArrayList<>();
        findGuidingEvents();
    }

    /**
     * Initializes the trust object with the root event of the current graph.
     * <p>
     * This method adds the root event of the {@link #currentGraph} to the {@link Trust#getAllEvents()}.
     * </p>
     */
    private void noGuidingGraph() {
        System.out.println("[Trust Strategy Message] : The guiding execution graph is empty");
        Optional<Event> event = Optional.of(Objects.requireNonNull(currentGraph.getRoot()).getValue());
        event.ifPresent(value -> trust.getAllEvents().add(value));
    }

    /**
     * Computes the ordered list of the guiding events based on the guiding execution graph.
     * <p>
     * The following method is used to compute the ordered list of the guiding events from the
     * {@link #guidingExecutionGraph}. Base on the {@link ExecutionGraph#getSc()} of the {@link #guidingExecutionGraph},
     * it finds the order of the events that if it picks the first event, the second event does not have any left hand
     * side in the {@link ExecutionGraph#getSc()} of the {@link #guidingExecutionGraph} except the first event.
     * <br>
     * Note that the {@link ExecutionGraph#getGraphEvents()} (G.E in the trust paper) and
     * {@link ExecutionGraph#getEventsOrder()} (\geq_{G} in the trust paper) of the {@link #guidingExecutionGraph} are
     * not reliable to find the guiding events. Since a forward revisit can violate the happens-before relation between
     * the events of these two list.
     * </p>
     */
    public void findGuidingEvents() {
        List<Event> freeEvents = new ArrayList<>();
        freeEvents.add(guidingNode.getValue());

        List<Event> remainingEvents = new ArrayList<>(guidingExecutionGraph.getGraphEvents());
        remainingEvents.remove(guidingNode.getValue()); // remove the initialization event

        while (!remainingEvents.isEmpty()) {
            remainingEvents.removeIf(event -> {
                boolean isEventFree = guidingExecutionGraph.getSc().stream()
                        .noneMatch(pair -> pair.getSecond().equals(event) && !freeEvents.contains(pair.getFirst()));
                if (isEventFree) {
                    freeEvents.add(event);
                }
                return isEventFree;
            });
        }

        guidingEvents.addAll(freeEvents);
        guidingEvents.remove(0);
    }

    /**
     * Represents the required strategy for the next start event.
     * <p>
     * This method represents the required strategy for the next start event. It creates a {@link StartEvent} for the
     * corresponding starting a thread request of a thread. The created {@link StartEvent} is added to the
     * {@link #currentGraph} if the {@link #guidingActivate} is true. Otherwise, it passes the event to the {@link #trust}
     * model checker.
     * </p>
     *
     * @param calleeThread is the thread that is going to be started.
     * @param callerThread is the thread that is going to call the start method of the calleeThread.
     */
    @Override
    public void nextStartEvent(Thread calleeThread, Thread callerThread) {
        StartEvent st = RuntimeEnvironment.createStartEvent(calleeThread, callerThread);
        RuntimeEnvironment.eventsRecord.add(st);
        if (guidingActivate) {
            addEventToCurrentGraph(st);
        } else {
            passEventToTrust(st);
        }
    }

    /**
     * Passes the given event to the {@link #trust} model checker.
     * <p>
     * This method passes the event to the trust model checker. It sets the {@link Trust#getAllGraphs()} ()} with an
     * empty list to make sure that the {@link #trust} model checker does not have any previous graphs. Then, it calls
     * the {@link Trust#visit(ExecutionGraph, List)} method to visit the current graph with the given event.
     * </p>
     *
     * @param event is the event that is going to be passed to the {@link #trust} model checker.
     */
    private void passEventToTrust(Event event) {
        List<Event> tempEventList = new ArrayList<>();
        tempEventList.add(event);
        trust.setAllGraphs(new ArrayList<>());
        trust.visit(currentGraph, tempEventList);
    }

    /**
     * Adds the given event to the current graph.
     * <p>
     * This method adds the event to the current graph. It sets the {@link #currentGraph} with the given event.
     * </p>
     *
     * @param event is the event that is going to be added to the current graph.
     */
    private void addEventToCurrentGraph(Event event) {
        currentGraph.addEvent(event);
    }

    /**
     * Represents the required strategy for the next enter monitor event.
     * <p>
     * This method represents the required strategy for the next enter monitor event. It prints a message that the
     * current version of the trust strategy does not support the enter monitor event and exits the program.
     * </p>
     *
     * @param thread  is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     */
    @Override
    public void nextEnterMonitorEvent(Thread thread, Object monitor) {
        System.out.println("[Trust Strategy Message] : This version of Trust does not support the enter monitor event");
        System.exit(0);
    }

    /**
     * Represents the required strategy for the next exit monitor event.
     * <p>
     * This method represents the required strategy for the next exit monitor event. It prints a message that the
     * current version of the trust strategy does not support the exit monitor event and exits the program.
     * </p>
     *
     * @param thread  is the thread that is going to exit the monitor.
     * @param monitor is the monitor that is going to be exited by the thread.
     */
    @Override
    public void nextExitMonitorEvent(Thread thread, Object monitor) {
        System.out.println("[Trust Strategy Message] : This version of Trust does not support the exit monitor event");
        System.exit(0);
    }

    /**
     * Represents the required strategy for the next join event.
     * <p>
     * This method represents the required strategy for the next join event. It creates a {@link JoinEvent} for the
     * corresponding joining a thread request of a thread. The created {@link JoinEvent} is added to the
     * {@link #currentGraph} if the {@link #guidingActivate} is true. Otherwise, it passes the event to the {@link #trust}
     * model checker.
     * </p>
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     */
    @Override
    public void nextJoinEvent(Thread joinReq, Thread joinRes) {
        JoinEvent joinEvent = RuntimeEnvironment.createJoinEvent(joinReq, joinRes);
        RuntimeEnvironment.eventsRecord.add(joinEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(joinEvent);
        } else {
            passEventToTrust(joinEvent);
        }
    }

    /**
     * Represents the required strategy for the next join request.
     * <p>
     * This method represents the required strategy for the next join request. It calls the
     * {@link #nextJoinEvent(Thread, Thread)} if the {@link #guidingActivate} is true. Otherwise, it puts the join request
     * and join response in the {@link RuntimeEnvironment#joinRequest} map and picks the next random thread.
     * </p>
     *
     * @param joinReq is the thread that is going to join another thread.
     * @param joinRes is the thread that is going to be joined by another thread.
     * @return the next thread that is going to be executed.
     */
    @Override
    public Thread nextJoinRequest(Thread joinReq, Thread joinRes) {
        if (guidingActivate) {
            nextJoinEvent(joinReq, joinRes);
            return joinReq;
        } else {
            RuntimeEnvironment.joinRequest.put(joinReq, joinRes);
            return pickNextRandomThread();
        }
    }

    /**
     * Represents the required strategy for the next enter monitor request.
     *
     * @param thread  is the thread that is going to enter the monitor.
     * @param monitor is the monitor that is going to be entered by the thread.
     */
    @Override
    public Thread nextEnterMonitorRequest(Thread thread, Object monitor) {
        System.out.println("[Trust Strategy Message] : This version of Trust does not support the enter monitor event");
        System.exit(0);
        return null;
    }

    /**
     * Represents the required strategy for the next read event.
     * <p>
     * This method represents the required strategy for the next read event. It passes the read event to the {@link #trust}
     * model checker if the {@link #guidingActivate} is false. Otherwise, it adds the read event to the current graph
     * and adds the reads-from edge to the {@link #currentGraph}.
     * </p>
     *
     * @param readEvent is the read event that is going to be executed.
     */
    @Override
    public void nextReadEvent(ReadEvent readEvent) {
        RuntimeEnvironment.eventsRecord.add(readEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(readEvent);
            addRfEdgeToCurrentGraph(readEvent);
        } else {
            passEventToTrust(readEvent);
            updateCurrentGraph(readEvent);
        }
    }

    /**
     * Adds the reads-from edge to the current graph.
     * <p>
     * This method adds the reads-from edge to the {@link #currentGraph}. It sets the reads-from edge of the read event
     * to the {@link #currentGraph}.
     * </p>
     *
     * @param readEvent is the read event that is going to be executed.
     */
    private void addRfEdgeToCurrentGraph(ReadEvent readEvent) {
        Optional<ReadsFrom> readsFrom = Optional.ofNullable(findRfEdge((ReadEvent) guidingEvent));
        readsFrom.ifPresent(readEvent::setRf);
    }

    /**
     * Finds the reads-from edge of the read event.
     * <p>
     * This method finds the reads-from edge of the read event. It returns the reads-from corresponding event to the
     * read event.
     * </p>
     *
     * @param readEvent is the read event that is going to be executed.
     * @return the corresponding reads-from event to the read event.
     */
    private ReadsFrom findRfEdge(ReadEvent readEvent) {
        ReadsFrom readsFrom;

        if (readEvent.getRf() instanceof InitializationEvent) {
            readsFrom = (ReadsFrom) currentGraph.getGraphEvents().get(0);
        } else {
            WriteEvent tempWrite = (WriteEvent) readEvent.getRf();

            readsFrom = currentGraph.getGraphEvents().stream()
                    .filter(event -> event instanceof WriteEvent)
                    .map(event -> (WriteEvent) event)
                    .filter(writeEvent -> writeEvent.getTid() == Objects.requireNonNull(tempWrite).getTid() &&
                            writeEvent.getSerial() == tempWrite.getSerial())
                    .findFirst()
                    .orElse(null);
        }

        return readsFrom;
    }

    /**
     * Updates the current graph.
     * <p>
     * This method updates the {@link #currentGraph}. It sets the {@link #currentGraph} with the new graphs that are
     * available in the {@link Trust#getAllGraphs()}. If there is only one new graph, it sets the {@link #currentGraph}
     * with the new graph. If there are more than one new graphs, it finds the extending graph. Otherwise, it creates a
     * new graph and sets the {@link #currentGraph} with the new graph.
     * </p>
     *
     * @param threadEvent is the thread event that is going to be executed.
     */
    private void updateCurrentGraph(ThreadEvent threadEvent) {
        List<ExecutionGraph> newGraphs = trust.getAllGraphs();
        if (newGraphs.size() == 1) {
            System.out.println("[Trust Strategy Message] : There is only one new graph");
            currentGraph = newGraphs.get(0);
        } else if (newGraphs.size() > 1) {
            findExtendingGraph(newGraphs, threadEvent);
        } else {
            int numOfGraphs = trust.getGraphCounter() + 1;
            trust.setGraphCounter(numOfGraphs);
            currentGraph.visualizeGraph(numOfGraphs, executionGraphsPath);
            System.out.println("[Trust Strategy Message] : There is no new graph from model checker");
            System.out.println("[Trust Strategy Message] : visited full execution graph G_" + numOfGraphs);
        }
    }

    /**
     * Finds the extending graph of the current graph.
     * <p>
     * This method finds the extending graph of the current graph. It adds the new graphs to the {@link #mcGraphs} list.
     * Then, it iterates over the {@link ExecutionGraph#getSc()} of each new graph and checks whether the left hand side
     * of the pair is equal to the given thread event. If it is not equal, it sets the {@link #currentGraph} with the
     * new graph. Otherwise, it removes the new graph from the {@link #mcGraphs} list.
     * </p>
     *
     * @param newGraphs   is the list of new graphs that are going to be checked.
     * @param threadEvent is the thread event that is going to be checked.
     */
    public void findExtendingGraph(List<ExecutionGraph> newGraphs, ThreadEvent threadEvent) {
        mcGraphs.addAll(newGraphs);

        newGraphs.stream()
                .filter(graph -> isValidGraph(graph, threadEvent))
                .findFirst()
                .ifPresent(graph -> {
                    currentGraph = graph;
                    mcGraphs.remove(graph);
                });

        System.out.println("[Trust Strategy Message] : The chosen graph is : " + currentGraph.getId());
    }

    /**
     * Checks whether the graph is valid or not.
     * <p>
     * This method checks whether the graph is valid or not. It returns true if the {@link ExecutionGraph#getSc()} of the
     * graph does not contain the given thread event. Otherwise, it returns false.
     * </p>
     *
     * @param graph       is the graph that is going to be checked.
     * @param threadEvent is the thread event that is going to be checked.
     * @return true if the graph is valid, otherwise false.
     */
    private boolean isValidGraph(ExecutionGraph graph, ThreadEvent threadEvent) {
        return graph.getSc().stream()
                .noneMatch(pair -> pair.component1().getType() == threadEvent.getType() &&
                        ((ThreadEvent) pair.component1()).getTid() == threadEvent.getTid() &&
                        ((ThreadEvent) pair.component1()).getSerial() == threadEvent.getSerial());
    }

    /**
     * Represents the required strategy for the next write event.
     * <p>
     * This method represents the required strategy for the next write event. It passes the write event to the
     * {@link #trust} model checker if the {@link #guidingActivate} is false. Otherwise, it adds the write event to the
     * current graph and adds the reads-from edge to the {@link #currentGraph}.
     * </p>
     *
     * @param writeEvent is the write event that is going to be executed.
     */
    @Override
    public void nextWriteEvent(WriteEvent writeEvent) {
        RuntimeEnvironment.eventsRecord.add(writeEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(writeEvent);
        } else {
            passEventToTrust(writeEvent);
            updateCurrentGraph(writeEvent);
        }
    }

    /**
     * Represents the required strategy for the next finish event.
     * <p>
     * This method represents the required strategy for the next finish event. It creates a {@link FinishEvent} for the
     * corresponding finishing execution request of a thread. The created {@link FinishEvent} is added to the
     * {@link #currentGraph} if the {@link #guidingActivate} is true. Otherwise, it passes the event to the {@link #trust}
     * model checker. The method also analyzes the suspended threads for joining the finished thread.
     * </p>
     *
     * @param thread is the thread that is going to be finished.
     */
    @Override
    public void nextFinishEvent(Thread thread) {
        FinishEvent finishEvent = RuntimeEnvironment.createFinishEvent(thread);
        RuntimeEnvironment.eventsRecord.add(finishEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(finishEvent);
            analyzeSuspendedThreadsForJoin(thread);
        } else {
            passEventToTrust(finishEvent);
            analyzeSuspendedThreadsForJoin(thread);
        }
    }

    /**
     * Represents the required strategy for the next finish request.
     * <p>
     * This method represents the required strategy for the next finish request. It calls the
     * {@link #nextFinishEvent(Thread)} and picks the next thread based on the guiding execution graph if the
     * {@link #guidingActivate} is true. Otherwise, it picks the next random thread.
     * </p>
     *
     * @param thread is the thread that is going to be finished.
     * @return the next thread that is going to be executed.
     */
    @Override
    public Thread nextFinishRequest(Thread thread) {
        nextFinishEvent(thread);
        if (guidingActivate) {
            return pickNextGuidedThread();
        } else {
            return pickNextRandomThread();
        }
    }

    @Override
    public void nextFailureEvent(Thread thread) {
        FailureEvent failureEvent = RuntimeEnvironment.createFailureEvent(thread);
        RuntimeEnvironment.eventsRecord.add(failureEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(failureEvent);
        } else {
            passEventToTrust(failureEvent);
        }
    }

    @Override
    public void nextDeadlockEvent(Thread thread) {
        DeadlockEvent deadlockEvent = RuntimeEnvironment.createDeadlockEvent(thread);
        RuntimeEnvironment.eventsRecord.add(deadlockEvent);
        if (guidingActivate) {
            addEventToCurrentGraph(deadlockEvent);
        } else {
            passEventToTrust(deadlockEvent);
        }
    }

    /**
     * Picks the next guided thread.
     * <p>
     * This method picks the next guided thread. It checks whether the {@link #guidingEvents} is empty or not. If it is
     * empty, it calls the {@link #handleEmptyGuidingEvents()} method and picks the next random thread. Otherwise, it
     * picks the next guided thread based on the {@link #guidingEvents} list. If the {@link #guidingEvent} is an instance
     * of {@link StartEvent}, it finds the guiding thread from the {@link #guidingExecutionGraph}. Otherwise, it sets the
     * {@link #guidingThread} with the thread id of the {@link #guidingEvent}.
     * </p>
     *
     * @return the next guided thread that is going to be executed.
     */
    public Thread pickNextGuidedThread() {
        if (guidingEvents.isEmpty()) {
            handleEmptyGuidingEvents();
            return pickNextRandomThread();
        }

        guidingEvent = guidingEvents.remove(0);
        if (guidingEvent instanceof StartEvent) {
            guidingThread = findGuidingThreadFromStartEvent();
        } else {
            guidingThread = ((ThreadEvent) guidingEvent).getTid();
        }

        System.out.println("[Trust Strategy Message] : Thread-" + guidingThread + " is selected to run");
        return RuntimeEnvironment.threadObjectMap.get((long) guidingThread);
    }

    /**
     * Finds the guiding thread from the start event.
     * <p>
     * This method finds the guiding thread from the start event. It returns the thread id of the start event that is
     * available in the {@link #guidingExecutionGraph}.
     * </p>
     *
     * @return the thread id of the start event.
     */
    private int findGuidingThreadFromStartEvent() {
        return guidingExecutionGraph.getSTs().stream()
                .filter(pair -> pair.component2().equals(guidingEvent))
                .map(pair -> ((ThreadEvent) pair.component1()).getTid())
                .findFirst()
                .orElse(0);
    }

    /**
     * Handles the empty guiding events.
     * <p>
     * This method handles the empty guiding events. It prints a message that the guiding events is empty and finds the
     * new COs, STs, and JTs based on the current graph. Then, it sets the {@link #guidingActivate} to false.
     * </p>
     */
    private void handleEmptyGuidingEvents() {
        System.out.println("[Trust Strategy Message] : The guidingEvents is empty");
        currentGraph.setCOs(findNewCOs());
        currentGraph.setSTs(findNewSTs());
        currentGraph.setJTs(findNewJTs());
        guidingActivate = false;
    }

    /**
     * Finds the new COs based on the current graph.
     * <p>
     * This method finds the new COs based on the current graph. It iterates over the {@link ExecutionGraph#getCOs()} of
     * the {@link #guidingExecutionGraph} and finds the new COs based on the current graph. It returns the new COs.
     * </p>
     *
     * @return the new COs based on the current graph.
     */
    private List<CO> findNewCOs() {
        List<CO> newCOs = new ArrayList<>();
        for (CO co : guidingExecutionGraph.getCOs()) {
            ReadsFrom firstWrite;
            WriteEvent secondWrite;

            if (co.getFirstWrite() instanceof InitializationEvent) {
                firstWrite = (ReadsFrom) currentGraph.getGraphEvents().get(0);
            } else {
                firstWrite = findWriteEvent((WriteEvent) co.getFirstWrite());
            }
            secondWrite = findWriteEvent(co.getSecondWrite());

            if (firstWrite != null && secondWrite != null) {
                newCOs.add(new CO(firstWrite, secondWrite));
            }
        }
        return newCOs;
    }

    /**
     * Finds the write event based on the current graph.
     * <p>
     * This method finds the write event based on the current graph. It returns the write event that is available in the
     * {@link #currentGraph}.
     * </p>
     *
     * @param tempWrite is the write event that is going to be found.
     * @return the write event that is available in the current graph.
     */
    private WriteEvent findWriteEvent(WriteEvent tempWrite) {
        return currentGraph.getGraphEvents().stream()
                .filter(event -> event instanceof WriteEvent)
                .map(event -> (WriteEvent) event)
                .filter(writeEvent -> writeEvent.getTid() == tempWrite.getTid() &&
                        writeEvent.getSerial() == tempWrite.getSerial())
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the new STs based on the current graph.
     * <p>
     * This method finds the new STs based on the current graph. It iterates over the {@link ExecutionGraph#getSTs()} of
     * the {@link #guidingExecutionGraph} and finds the new STs based on the current graph. It returns the new STs.
     * </p>
     *
     * @return the new STs based on the current graph.
     */
    private Set<Pair<Event, Event>> findNewSTs() {
        Set<Pair<Event, Event>> newSTs = new HashSet<>();
        for (Pair<Event, Event> st : guidingExecutionGraph.getSTs()) {
            ThreadEvent firstThreadEvent = findThreadEventInCurrentGraph((ThreadEvent) st.component1());
            ThreadEvent secondThreadEvent = findThreadEventInCurrentGraph((ThreadEvent) st.component2());
            if (firstThreadEvent != null && secondThreadEvent != null) {
                newSTs.add(new Pair<>(firstThreadEvent, secondThreadEvent));
            }
        }
        return newSTs;
    }

    /**
     * Finds the thread event based on the current graph.
     * <p>
     * This method finds the thread event based on the current graph. It returns the thread event that is available in the
     * {@link #currentGraph}.
     * </p>
     *
     * @param tempThreadEvent is the thread event that is going to be found.
     * @return the thread event that is available in the current graph.
     */
    private ThreadEvent findThreadEventInCurrentGraph(ThreadEvent tempThreadEvent) {
        return currentGraph.getGraphEvents().stream()
                .filter(event -> event instanceof ThreadEvent)
                .map(event -> (ThreadEvent) event)
                .filter(threadEvent -> threadEvent.getTid() == tempThreadEvent.getTid() &&
                        threadEvent.getSerial() == tempThreadEvent.getSerial() &&
                        threadEvent.getType() == tempThreadEvent.getType())
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the new JTs based on the current graph.
     * <p>
     * This method finds the new JTs based on the current graph. It iterates over the {@link ExecutionGraph#getJTs()} of
     * the {@link #guidingExecutionGraph} and finds the new JTs based on the current graph. It returns the new JTs.
     * </p>
     *
     * @return the new JTs based on the current graph.
     */
    private Set<Pair<Event, Event>> findNewJTs() {
        Set<Pair<Event, Event>> newJTs = new HashSet<>();
        for (Pair<Event, Event> jt : guidingExecutionGraph.getJTs()) {
            ThreadEvent firstThreadEvent = findThreadEventInCurrentGraph((ThreadEvent) jt.component1());
            ThreadEvent secondThreadEvent = findThreadEventInCurrentGraph((ThreadEvent) jt.component2());
            if (firstThreadEvent != null && secondThreadEvent != null) {
                newJTs.add(new Pair<>(firstThreadEvent, secondThreadEvent));
            }
        }
        return newJTs;
    }

    /**
     * Picks the next thread.
     * <p>
     * This method picks the next thread that is going to be executed. It returns the next guided thread if the
     * {@link #guidingActivate} is true. Otherwise, it returns the next random thread.
     * </p>
     *
     * @return the next thread that is going to be executed.
     */
    @Override
    public Thread pickNextThread() {
        if (guidingActivate) {
            return pickNextGuidedThread();
        } else {
            return pickNextRandomThread();
        }
    }

    /**
     * Indicates whether the execution is done or not.
     * <p>
     * This method indicates whether the execution is done or not. It returns true if the
     * {@link RuntimeEnvironment#tempMcGraphs} is empty. Otherwise, it returns false.
     * </p>
     *
     * @return true if the execution is done, otherwise false.
     */
    @Override
    public boolean done() {
        return RuntimeEnvironment.tempMcGraphs.isEmpty();
    }

    /**
     * Saves the execution graphs to a file.
     * <p>
     * This method saves the execution graphs to a file. It creates a new file with the given file name and writes the
     * execution graphs to the file.
     * </p>
     *
     * @param executionGraphs is the list of execution graphs that are going to be saved.
     * @param fileName        is the name of the file that is going to be created.
     * @throws IllegalArgumentException if the execution graphs or the file name is null.
     * @throws RuntimeException         if an error occurred while saving the graphs to the file.
     */
    public void saveGraphsToFile(List<ExecutionGraph> executionGraphs, String fileName) {
        if (executionGraphs == null || fileName == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }

        try (FileOutputStream fileOut = new FileOutputStream(fileName);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(executionGraphs);
        } catch (IOException e) {
            throw new RuntimeException("Error occurred while saving graphs to file", e);
        }
    }

    /**
     * Loads the execution graphs from a file.
     * <p>
     * This method loads the execution graphs from a file. It reads the execution graphs from the file and returns the
     * loaded graphs.
     * </p>
     *
     * @param fileName is the name of the file that is going to be read.
     * @return the loaded execution graphs.
     * @throws IllegalArgumentException if the file name is null or empty.
     * @throws RuntimeException         if an error occurred while loading the graphs from the file.
     */
    @SuppressWarnings("unchecked")
    public List<ExecutionGraph> loadGraphsFromFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("File name cannot be null or empty");
        }

        List<ExecutionGraph> loadedGraphs;

        try (FileInputStream fileIn = new FileInputStream(fileName);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            Object object = in.readObject();
            if (object instanceof List) {
                loadedGraphs = (List<ExecutionGraph>) object;
            } else {
                throw new RuntimeException("The object is not an instance of List");
            }
        } catch (IOException i) {
            throw new RuntimeException("Error occurred while loading graphs from file", i);
        } catch (ClassNotFoundException c) {
            throw new RuntimeException("Class not found while loading graphs from file", c);
        }

        return loadedGraphs;
    }

    /**
     * Saves the buggy execution trace.
     * <p>
     * This method saves the buggy execution trace. It writes the {@link RuntimeEnvironment#eventsRecord} to the file.
     * </p>
     */
    @Override
    public void saveBuggyExecutionTrace() {
        try {
            FileOutputStream fileOut = new FileOutputStream(buggyTracePath + buggyTraceFile);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(RuntimeEnvironment.eventsRecord);
            out.close();
            fileOut.close();
            System.out.println("[Trust Strategy Message] : Buggy execution trace is saved in " + buggyTracePath +
                    buggyTraceFile);
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    /**
     * Saves the execution state.
     * <p>
     * This method saves the execution state. It saves the {@link #mcGraphs} to the file and loads the saved graphs
     * to the {@link RuntimeEnvironment#tempMcGraphs} list. It also sets the {@link RuntimeEnvironment#numOfGraphs}
     * with the number of graphs that are available in the {@link Trust#getGraphCounter()}.
     * </p>
     */
    @Override
    public void saveExecutionState() {
        saveGraphsToFile(mcGraphs, "src/main/resources/ObjectStore/mcGraphs.obj");
        List<ExecutionGraph> savedGraphs = loadGraphsFromFile("src/main/resources/ObjectStore/mcGraphs.obj");
        RuntimeEnvironment.tempMcGraphs.addAll(savedGraphs);
        RuntimeEnvironment.numOfGraphs = trust.getGraphCounter();
    }
}